package csw.youtube.chat.live.service;

import com.github.pemistahl.lingua.api.Language;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import csw.youtube.chat.live.js.YouTubeChatScriptProvider;
import csw.youtube.chat.live.model.ScraperState;
import csw.youtube.chat.playwright.PlaywrightBrowserManager;
import csw.youtube.chat.profanity.service.ProfanityLogService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static csw.youtube.chat.live.js.YouTubeChatScriptProvider.getMutationObserverScript;
import static csw.youtube.chat.live.service.YTRustScraperService.YOUTUBE_WATCH_URL;
import static csw.youtube.chat.live.service.YTRustScraperService.validateVideoId;

@Slf4j
@Service
public class YTPlaywrightScraperService {
    private static final int POLL_INTERVAL_MS = 1000;
    private static final int PLAYWRIGHT_TIMEOUT_MS = 10000;

    @Getter
    private final Map<String, CompletableFuture<Void>> activeFutures = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, ScraperState> scraperStates = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, String> videoThreadNames = new ConcurrentHashMap<>();

    private final ProfanityLogService profanityLogService;
    private final RankingService rankingService;
    private final PlaywrightBrowserManager playwrightBrowserManager;
    private final Executor chatScraperExecutor;

    public YTPlaywrightScraperService(
            ProfanityLogService profanityLogService,
            RankingService rankingService,
            @Qualifier("chatScraperExecutor") Executor chatScraperExecutor,
            PlaywrightBrowserManager playwrightBrowserManager) {
        this.profanityLogService = profanityLogService;
        this.rankingService = rankingService;
        this.chatScraperExecutor = chatScraperExecutor;
        this.playwrightBrowserManager = playwrightBrowserManager;
    }

    // Core Functionality

    @Async("chatScraperExecutor")
    public CompletableFuture<Void> scrapeChannel(String videoId, Set<Language> skipLangs) {
        validateVideoId(videoId);

        if (activeFutures.containsKey(videoId)) {
            log.warn("Scraper already running for video {}. Ignoring new request.", videoId);
            return CompletableFuture.completedFuture(null);
        }

        var state = initializeScraperState(videoId);
        state.setSkipLangs(skipLangs);
        var scraperFuture = new CompletableFuture<Void>();
        activeFutures.put(videoId, scraperFuture);

        chatScraperExecutor.execute(() -> {
            try {
                playwrightBrowserManager.withPage(page -> {
                    try {
                        scrapeChat(page, videoId, state, scraperFuture, skipLangs);
                    } catch (Exception e) {
                        handleException(videoId, e, scraperFuture, state);
                    }
                });
            } catch (Exception outerEx) {
                handleException(videoId, outerEx, scraperFuture, state);
            }
        });

        return scraperFuture;
    }

    public String stopScraper(String videoId) {
        var future = activeFutures.remove(videoId);
        if (future == null) return "No active scraper found for video ID: " + videoId;

        future.complete(null);
        updateStateOnStop(videoId);
        log.info("Stopping scraper requested for video ID: {}", videoId);
        return "Stopping scraper for video ID: " + videoId;
    }

    // Private Helper Methods

    private void closePageSafely(Page page, String videoId) {
        try {
            page.close();
        } catch (Exception closeEx) {
            log.warn("Error closing page for video {}: {}", videoId, closeEx.getMessage());
        }
    }

    private void extractAndSetVideoInfo(Page page, String videoId, ScraperState state) {
        var videoTitle = extractVideoTitle(page);
        var channelName = extractChannelName(page);
        log.info("Video Title: '{}', Channel Name: '{}' for video {}", videoTitle, channelName, videoId);
        state.setVideoTitle(videoTitle);
        state.setChannelName(channelName);
    }

    private String extractChannelName(Page page) {
        log.debug("Extracting channel name.");
        return page.evaluate(YouTubeChatScriptProvider.extractChannelName()).toString();
    }

    private String extractVideoTitle(Page page) {
        log.debug("Extracting video title.");
        page.waitForSelector("ytd-watch-metadata",
                new Page.WaitForSelectorOptions().setTimeout(PLAYWRIGHT_TIMEOUT_MS));
        return page.evaluate(YouTubeChatScriptProvider.extractVideoTitle()).toString();
    }

    private void handleException(String videoId, Exception ex, CompletableFuture<Void> scraperFuture, ScraperState state) {
        log.error("Error in scrapeChannel for video {}", videoId, ex);
        scraperFuture.completeExceptionally(ex);
        state.setStatus(ScraperState.Status.FAILED);
        state.setReason("Outer error: " + ex.getMessage());
    }

    private ScraperState initializeScraperState(String videoId) {
        var state = new ScraperState(videoId);
        state.setStatus(ScraperState.Status.IDLE);
        scraperStates.put(videoId, state);

        var threadName = Thread.currentThread().getName();
        videoThreadNames.put(videoId, threadName);
        state.setThreadName(threadName);
        state.setCreatedAt(Instant.now());

        log.info("Starting chat scraper for video ID: {}", videoId);
        return state;
    }

    private boolean isChatPresent(Page page, String videoId) {
        try {
            return page.frameLocator("iframe#chatframe").owner().count() > 0;
        } catch (Exception e) {
            log.warn("Error checking chat iframe presence for video {}: {}", videoId, e.getMessage());
            return false;
        }
    }

    private void logThroughput(AtomicInteger messagesPerInterval, String videoId) {
        int count = messagesPerInterval.getAndSet(0);
        var state = scraperStates.get(videoId);
        if (state != null) {
            state.setLastThroughput(count);
            if (count > state.getMaxThroughput()) state.setMaxThroughput(count);

            long intervals = state.getIntervalsCount().incrementAndGet();
            double newAvg = intervals == 1 ? count : (state.getAverageThroughput() * (intervals - 1) + count) / intervals;
            state.setAverageThroughput(newAvg);
        }
    }

    private void navigateToVideo(Page page, String videoId) {
        var fullUrl = YOUTUBE_WATCH_URL + videoId;
        page.navigate(fullUrl);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        waitForChatIframe(page);
    }

    private void scrapeChat(Page page, String videoId, ScraperState state, CompletableFuture<Void> scraperFuture, Set<Language> skipLangs) {
        try {
            navigateToVideo(page, videoId);
            extractAndSetVideoInfo(page, videoId, state);

            var chatFrameLocator = page.frameLocator("iframe#chatframe");
            var chatBodyLocator = chatFrameLocator.locator("body");
            var chatMessagesLocator = chatFrameLocator.locator("div#items yt-live-chat-text-message-renderer");
            var iframePage = chatBodyLocator.page();
            waitForInitialMessages(chatMessagesLocator);

            state.setStatus(ScraperState.Status.RUNNING);

            var messagesPerInterval = new AtomicInteger(0);
            setupChatScripts(iframePage, chatBodyLocator, messagesPerInterval, videoId, state, skipLangs);

            try (var scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory())) {
                var logTask = scheduler.scheduleAtFixedRate(
                        () -> logThroughput(messagesPerInterval, videoId),
                        10, 10, TimeUnit.SECONDS
                );

                while (!scraperFuture.isDone()) {
                    page.waitForTimeout(POLL_INTERVAL_MS);
                    if (!isChatPresent(page, videoId)) {
                        log.warn("❌ Chat iframe disappeared for video {}. Stopping scraper...", videoId);
                        scraperFuture.complete(null);
                    }
                }

                logTask.cancel(false);
                scheduler.shutdown();
            }

            if (!scraperFuture.isCompletedExceptionally()) {
                scraperFuture.complete(null);
                state.setStatus(ScraperState.Status.COMPLETED);
                log.info("Scraper for video {} completed normally.", videoId);
            }
        } catch (PlaywrightException pwe) {
            handlePlaywrightException(videoId, pwe);
            scraperFuture.completeExceptionally(pwe);
            state.setStatus(ScraperState.Status.FAILED);
            state.setReason(parsePlaywrightError(pwe));
        } catch (Exception ex) {
            log.error("Error in scraping logic for videoId={}: {}", videoId, ex.getMessage(), ex);
            scraperFuture.completeExceptionally(ex);
            state.setStatus(ScraperState.Status.FAILED);
            state.setReason("General error: " + ex.getMessage());
        } finally {
            closePageSafely(page, videoId);
        }
    }

    private void setupChatScripts(Page iframePage, Locator chatBodyLocator, AtomicInteger messagesPerInterval,
                                  String videoId, ScraperState state, Set<Language> skipLangs) {
        iframePage.evaluate(YouTubeChatScriptProvider.getExposeHandlerScript());
        iframePage.evaluate(YouTubeChatScriptProvider.getChatActivationScript());

        iframePage.exposeFunction("_ytChatHandler_onNewMessages", (args) -> {
            var username = (String) args[0];
            var messageText = (String) args[1];
            messagesPerInterval.incrementAndGet();
            state.getTotalMessages().incrementAndGet();

            profanityLogService.logIfProfane(username, messageText);
            chatScraperExecutor.execute(() -> rankingService.updateKeywordRanking(videoId, messageText, skipLangs));
            return null;
        });

        chatBodyLocator.evaluate(getMutationObserverScript());
    }

    private void updateStateOnStop(String videoId) {
        var state = scraperStates.get(videoId);
        if (state != null) {
            state.setStatus(ScraperState.Status.COMPLETED);
            state.setReason("Stopped by user.");
        }
    }

    private void waitForChatIframe(Page page) {
        page.waitForSelector("iframe#chatframe",
                new Page.WaitForSelectorOptions().setTimeout(PLAYWRIGHT_TIMEOUT_MS));
        log.debug("Waited for chat iframe to appear.");
    }

    private void waitForInitialMessages(Locator chatMessagesLocator) {
        chatMessagesLocator.first().waitFor(
                new Locator.WaitForOptions().setTimeout(PLAYWRIGHT_TIMEOUT_MS));
        log.debug("Waited for initial chat messages to load.");
    }

    private void handlePlaywrightException(String videoId, PlaywrightException pwe) {
        if (pwe.getMessage().contains("TargetClosedError")) {
            log.warn("Target page closed unexpectedly for video {}. Restarting scraper...", videoId);
        } else {
            log.error("Playwright error for video {}: {}", videoId, pwe.getMessage(), pwe);
        }
    }

    private String parsePlaywrightError(PlaywrightException e) {
        String rawMsg = e.getMessage();
        if (rawMsg == null) {
            return "Unknown Playwright error (no message).";
        }
        String lowerMsg = rawMsg.toLowerCase();

        if (lowerMsg.contains("timeouterror") || lowerMsg.contains("timeout 10000ms exceeded")) {
            // This likely means the chat iframe didn't appear in time
            return "Chat iframe not found. The video may not be live or chat is disabled.";
        }
        if (lowerMsg.contains("net::err_name_not_resolved")) {
            return "Network issue: could not resolve the host. Check your internet connection or URL.";
        }
        if (lowerMsg.contains("net::err_internet_disconnected")) {
            return "No internet connection, or YouTube is unreachable.";
        }
        // Fallback if none of the above patterns match:
        return "Playwright error: " + rawMsg;
    }
}