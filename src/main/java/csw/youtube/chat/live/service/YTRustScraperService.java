package csw.youtube.chat.live.service;

import com.github.pemistahl.lingua.api.Language;
import csw.youtube.chat.live.dto.SimpleChatMessage;
import csw.youtube.chat.live.model.ScraperState;
import csw.youtube.chat.live.model.ScraperTask;
import csw.youtube.chat.profanity.service.ProfanityLogService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.RedissonShutdownException;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Service for scraping YouTube Live Chat messages for a given video ID using Redis for queueing.
 */
@Slf4j
@Service
public class YTRustScraperService {
    public static final String YOUTUBE_WATCH_URL = "https://www.youtube.com/watch?v=";
    private static final Duration FAILED_SCRAPER_CLEANUP_THRESHOLD = Duration.ofMinutes(5);
    private static final int MAX_CONCURRENT_SCRAPERS = 5;
    private static final String RUST_SCRAPER_PATH = "src/main/resources/ytchatx-scraper.exe";
    private volatile boolean isShuttingDown = false;
    private Thread queueProcessorThread;

    @Getter
    private final Map<String, ScraperState> scraperStates = new ConcurrentHashMap<>();
    private final ProfanityLogService profanityLogService;
    private final RankingService rankingService;
    private final Executor chatScraperExecutor;
    private final Map<String, Process> activeScrapers = new ConcurrentHashMap<>();
    private final RBlockingQueue<ScraperTask> scraperQueue;
    private final RSemaphore scraperSemaphore;

    public YTRustScraperService(
            ProfanityLogService profanityLogService,
            RankingService rankingService,
            @Qualifier("chatScraperExecutor") Executor chatScraperExecutor,
            RedissonClient redissonClient) {
        this.profanityLogService = profanityLogService;
        this.rankingService = rankingService;
        this.chatScraperExecutor = chatScraperExecutor;
        this.scraperQueue = redissonClient.getBlockingQueue("scraperQueue");
        this.scraperQueue.clear();

        this.scraperSemaphore = redissonClient.getSemaphore("scraperSemaphore");
        // Delete any old semaphore value so that we can set it freshly.
        scraperSemaphore.delete();
        scraperSemaphore.trySetPermits(MAX_CONCURRENT_SCRAPERS);
    }

    // Lifecycle Methods

    @Scheduled(fixedRate = 600_000)
    public void cleanupFailedScrapers() {
        Instant now = Instant.now();
        scraperStates.entrySet().stream()
                .filter(entry -> isFailedAndExpired(entry.getValue(), now))
                .forEach(entry -> {
                    scraperStates.remove(entry.getKey());
                    log.info("Removed failed scraper for video {} (exceeded timeout)", entry.getKey());
                });
    }

    @PostConstruct
    public void cleanupOrphanProcesses() {
        log.info("Checking for orphaned Rust scraper processes...");
        stopAllActiveScrapers();
        killOrphanedProcesses();
    }

    @PreDestroy
    public void onDestroy() {
        isShuttingDown = true;
        if (queueProcessorThread != null) {
            queueProcessorThread.interrupt();
        }
        stopAllActiveScrapers();
    }

    @PostConstruct
    public void startQueueProcessor() {
        queueProcessorThread = Thread.ofVirtual().start(this::processQueue);
    }

    // Core Functionality

    public void processChatMessages(String videoId, List<SimpleChatMessage> messages) {
        Set<Language> skipLangs = Optional.ofNullable(scraperStates.get(videoId))
                .map(ScraperState::getSkipLangs)
                .orElse(Collections.emptySet());

        messages.forEach(message -> {
            profanityLogService.logIfProfane(message.username(), message.message());
            chatScraperExecutor.execute(() -> {
                rankingService.updateLanguageStats(videoId, message.message());
                rankingService.updateKeywordRanking(videoId, message.message(), skipLangs);
            });
        });
    }

    public ScraperState getScraperState(String videoId) {
        return scraperStates.get(videoId);
    }

    public boolean startRustScraper(String videoId, Set<Language> skipLangs) {
        validateVideoId(videoId);

        var state = scraperStates.computeIfAbsent(videoId, _ -> new ScraperState(videoId, skipLangs));
        if (isScraperActive(state)) {
            log.warn("Scraper already running or queued for video {}", videoId);
            return false;
        }

        state.setStatus(ScraperState.Status.QUEUED);
        return enqueueScraperTask(videoId, skipLangs, state);
    }

    public String stopRustScraper(String videoId) {
        var process = activeScrapers.remove(videoId);
        if (process == null) return "No active scraper found for video ID: " + videoId;

        sendCommandToRust(process, "p\n");
        process.onExit().thenRun(() -> log.info("✅ Rust process for video {} exited cleanly.", videoId));
        updateStateOnStop(videoId);
        return "Scraper stopped for video ID: " + videoId;
    }

    // Private Helper Methods

    private List<String> buildCommand(String videoId, Set<Language> skipLangs) {
        List<String> command = new ArrayList<>(List.of(RUST_SCRAPER_PATH, "--video-id=" + videoId));
        if (!skipLangs.isEmpty()) {
            command.add("--skip-langs=" + skipLangs.stream().map(Enum::name).collect(Collectors.joining(",")));
        }
        return command;
    }

    private void captureProcessOutput(String videoId, Process process, AtomicBoolean semaphoreReleased) {
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().forEach(line -> {
                // Debug log to inspect each line received
                // log.debug("Scraper output for video {}: {}", videoId, line);

                if (line.contains("initiated") && !semaphoreReleased.get()) {
                    // log.info("Received 'initiated' signal from scraper for video {}. Releasing semaphore.", videoId);
                    scraperSemaphore.release();
                    semaphoreReleased.set(true);
                }
                if (line.contains("❌ Maximum retries reached")) {
                    log.error("Detected scraper failure for video {}. Stopping process.", videoId);
                    stopRustScraper(videoId);
                }
            });
        } catch (IOException e) {
            log.error("Error capturing output for {}", videoId, e);
            updateScraperStatusToFailed(videoId, "Communication with Rust scraper failed.");
        } finally {
            log.info("Scraper process for video {} has exited.", videoId);
        }
    }

    private void cleanupState(String videoId) {
        try {
            Thread.sleep(Duration.ofMinutes(5).toMillis());
            scraperStates.remove(videoId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean enqueueScraperTask(String videoId, Set<Language> skipLangs, ScraperState state) {
        try {
            scraperQueue.put(new ScraperTask(videoId, skipLangs));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to queue scraper for video {}", videoId, e);
            state.setStatus(ScraperState.Status.FAILED);
            state.setErrorMessage("Queueing failed: " + e.getMessage());
            return false;
        }
    }

    private boolean isFailedAndExpired(ScraperState state, Instant now) {
        return state.getStatus() == ScraperState.Status.FAILED &&
                Duration.between(state.getCreatedAt(), now).compareTo(FAILED_SCRAPER_CLEANUP_THRESHOLD) > 0;
    }

    private boolean isScraperActive(ScraperState state) {
        return state.getStatus() == ScraperState.Status.RUNNING || state.getStatus() == ScraperState.Status.QUEUED;
    }

    private void killOrphanedProcesses() {
        try {
            var os = System.getProperty("os.name").toLowerCase();
            var pb = os.contains("win")
                    ? new ProcessBuilder("taskkill", "/F", "/IM", "ytchatx-scraper.exe")
                    : new ProcessBuilder("pkill", "-f", "ytchatx-scraper");
            pb.start();
            log.info("✅ Orphaned Chrome & Rust scraper processes killed.");
        } catch (IOException e) {
            log.error("❌ Failed to clean up orphaned Chrome processes.", e);
        }
    }

    private void processQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                var task = scraperQueue.take();
                scraperSemaphore.acquire();
                if (!isShuttingDown) {
                    chatScraperExecutor.execute(() -> runScraper(task));
                } else {
                    log.info("Skipping task execution due to shutdown: {}", task.videoId());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Scraper queue processor shut down");
    }

    private void runScraper(ScraperTask task) {
        String videoId = task.videoId();
        var state = scraperStates.get(videoId);
        state.setStatus(ScraperState.Status.RUNNING);
        state.setThreadName(Thread.currentThread().getName());
        log.info("💽 Running scrapper for video {}...", videoId);

        try {
            var process = new ProcessBuilder(buildCommand(videoId, task.skipLangs()))
                    .redirectErrorStream(true)
                    .start();
            activeScrapers.put(videoId, process); // Thread-safe with ConcurrentHashMap

            // Create an atomic flag to ensure semaphore is released only once.
            AtomicBoolean semaphoreReleased = new AtomicBoolean(false);
            // Capture output asynchronously and wait for the "initiated" message to release the semaphore.
            Thread.ofVirtual().start(() -> captureProcessOutput(videoId, process, semaphoreReleased));

            int exitCode = process.waitFor();
            state.setStatus(exitCode == 0 ? ScraperState.Status.COMPLETED : ScraperState.Status.FAILED);
            if (exitCode != 0) state.setErrorMessage("Process exited with code " + exitCode);
        } catch (Exception e) {
            log.error("Error running scraper for video {}", videoId, e);
            state.setStatus(ScraperState.Status.FAILED);
            state.setErrorMessage(e.getMessage());
            scraperSemaphore.release();
        } finally {
            activeScrapers.remove(videoId);
            if (!isShuttingDown) {
                if (state.getStatus() != ScraperState.Status.RUNNING) {
                    chatScraperExecutor.execute(() -> cleanupState(videoId));
                }
            }
        }
    }

    private void sendCommandToRust(Process process, String command) {
        try (var writer = new OutputStreamWriter(process.getOutputStream())) {
            writer.write(command);
            writer.flush();
        } catch (IOException e) {
            log.error("Error sending command to Rust: {}", e.getMessage());
        }
    }

    private void stopAllActiveScrapers() {
        activeScrapers.keySet().forEach(this::stopRustScraper);
    }

    private void updateScraperStatusToFailed(String videoId, String errorMessage) {
        Optional.ofNullable(scraperStates.get(videoId))
                .ifPresent(state -> {
                    state.setStatus(ScraperState.Status.FAILED);
                    state.setErrorMessage(errorMessage);
                });
    }

    private void updateStateOnStop(String videoId) {
        Optional.ofNullable(scraperStates.get(videoId))
                .ifPresent(state -> {
                    state.setStatus(ScraperState.Status.COMPLETED);
                    state.setErrorMessage("Stopped by user.");
                });
    }

    protected static void validateVideoId(String videoId) {
        if (Objects.isNull(videoId) || videoId.trim().isEmpty()) {
            throw new IllegalArgumentException("Video ID cannot be null or empty.");
        }
    }
}