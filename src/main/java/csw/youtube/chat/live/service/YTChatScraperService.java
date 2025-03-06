package csw.youtube.chat.live.service;

import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import csw.youtube.chat.live.dto.ChatMessage;
import csw.youtube.chat.live.js.YouTubeChatScriptProvider;
import csw.youtube.chat.live.model.ScraperState;
import csw.youtube.chat.playwright.PlaywrightBrowserManager;
import csw.youtube.chat.profanity.service.ProfanityLogService;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for scraping YouTube Live Chat messages for a given video ID.
 * <p>
 * TODO Auto-stop low-activity scrapers (like `totalMessages < 3`)
 * TODO Auto-clean COMPLETED/FAILED scrapers (scheduling)
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class YTChatScraperService {

    public static final String YOUTUBE_WATCH_URL = "https://www.youtube.com/watch?v=";
    private static final int POLL_INTERVAL_MS = 1000;
    private static final int PLAYWRIGHT_TIMEOUT_MS = 10000; // For selectors and navigation

    @Getter
    // Keep track of active scrapers: videoId -> future
    private final Map<String, CompletableFuture<Void>> activeFutures = new ConcurrentHashMap<>();

    @Getter
    private final Map<String, ScraperState> scraperStates = new ConcurrentHashMap<>();

    @Getter
    // Map of videoId -> threadName (or some stable ID) for display
    private final Map<String, String> videoThreadNames = new ConcurrentHashMap<>();

    private final ChatBroadcastService chatBroadcastService;
    private final ProfanityLogService profanityLogService;
    private final KeywordRankingService keywordRankingService;

    /**
     * Executor for running chat scraping tasks asynchronously (uses virtual threads).
     */
    @Qualifier("chatScraperExecutor")
    private final Executor chatScraperExecutor;

    private final PlaywrightBrowserManager playwrightBrowserManager;

    @PreDestroy
    public void onDestroy() {
        // Stop all active scrapers so they don’t queue new tasks
        activeFutures.keySet().forEach(this::stopScraper);
    }

    /**
     * Starts scraping chat messages for a given YouTube video ID if a scraper is not already active.
     * This method is fully asynchronous (via @Async on a Virtual Thread executor) and will not block
     * the calling thread. It returns a CompletableFuture representing the ongoing scrape.
     * <p>
     * To stop the scraper, either complete or cancel this future externally.
     *
     * @param videoId The YouTube video ID to scrape.
     * @return A CompletableFuture that completes when the scraper finishes or is canceled.
     * @throws IllegalArgumentException if the videoId is null or empty.
     */
    @Async("chatScraperExecutor")
    public CompletableFuture<Void> scrapeChannel(String videoId) {
        validateVideoId(videoId);

        // Avoid starting duplicate scrapers
        if (activeFutures.containsKey(videoId)) {
            log.warn("Scraper already running for video {}. Ignoring new request.", videoId);
            return CompletableFuture.completedFuture(null);
        }

        // Initialize the scraper state and create the future
        ScraperState state = initializeScraperState(videoId);
        CompletableFuture<Void> scraperFuture = new CompletableFuture<>();
        activeFutures.put(videoId, scraperFuture);

        // Launch the scraping logic in a separate task
        chatScraperExecutor.execute(() -> {
            try {
                playwrightBrowserManager.withPage(page -> {
                    scrapeChat(page, videoId, state, scraperFuture);
                });
            } catch (Exception outerEx) {
                handleOuterException(videoId, outerEx, scraperFuture, state);
            } finally {
                // cleanupScraper(videoId);
            }
        });

        return scraperFuture;
    }

    /**
     * Stops the scraper for the given video ID if it is currently active.
     * If no scraper is active for the video ID, it returns a message indicating so.
     *
     * @param videoId The YouTube video ID to stop scraping.
     * @return A message indicating the status of the stop operation.
     */
    public String stopScraper(String videoId) {
        CompletableFuture<Void> future = activeFutures.remove(videoId);
        if (future == null) {
            return "No active scraper found for video ID: " + videoId;
        }

        // Completing the future signals the while-loop in scraping logic to exit.
        future.complete(null);

        ScraperState state = scraperStates.get(videoId);
        if (state != null) {
            state.setStatus(ScraperState.Status.COMPLETED);
            state.setErrorMessage("Stopped by user.");
        }
        log.info("Stopping scraper requested for video ID: {}", videoId);
        return "Stopping scraper for video ID: " + videoId;
    }

    // ------------------------------------------------------------------------
    //  Refactored Private Methods
    // ------------------------------------------------------------------------

    /**
     * Validates the given videoId to ensure it is not null or empty.
     */
    private void validateVideoId(String videoId) {
        if (videoId == null || videoId.trim().isEmpty()) {
            throw new IllegalArgumentException("Video ID cannot be null or empty.");
        }
    }

    /**
     * Initializes and stores a new {@link ScraperState} for the given video ID.
     */
    private ScraperState initializeScraperState(String videoId) {
        ScraperState state = new ScraperState(videoId);
        state.setStatus(ScraperState.Status.IDLE);
        scraperStates.put(videoId, state);

        String threadName = Thread.currentThread().getName();
        videoThreadNames.put(videoId, threadName);
        state.setThreadName(threadName);
        state.setCreatedAt(Instant.now());

        log.info("Starting chat scraper for video ID: {}", videoId);
        return state;
    }

    /**
     * Encapsulates the main scraping logic: navigating the page, injecting JS, observing messages,
     * and tracking throughput until the scraperFuture is signaled to complete.
     */
    private void scrapeChat(Page page, String videoId, ScraperState state, CompletableFuture<Void> scraperFuture) {
        try {
            // 1) Navigate and wait for DOM
            navigateToVideo(page, videoId);

            // 2) Extract video/channel info
            String videoTitle = extractVideoTitle(page);
            String channelName = extractChannelName(page);
            log.info("Video Title: '{}', Channel Name: '{}' for video {}", videoTitle, channelName, videoId);
            state.setChannelName(channelName);
            state.setVideoTitle(videoTitle);

            // 3) Get references to chat area
            FrameLocator chatFrameLocator = page.frameLocator("iframe#chatframe");
            Locator chatBodyLocator = chatFrameLocator.locator("body");
            Locator chatMessagesLocator = chatFrameLocator.locator("div#items yt-live-chat-text-message-renderer");
            Page iframePage = chatBodyLocator.page(); // The iframe's page
            waitForInitialMessages(chatMessagesLocator);

            state.setStatus(ScraperState.Status.RUNNING);

            // 4) Inject JavaScript and track messages
            AtomicInteger messagesPerInterval = new AtomicInteger(0);
            setupChatScripts(iframePage, chatBodyLocator, messagesPerInterval, videoId, videoTitle, channelName);

            // 5) Periodically log throughput while the scraper is running
            try (ScheduledExecutorService scheduler =
                         Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory())) {

                ScheduledFuture<?> logTask = scheduler.scheduleAtFixedRate(
                        () -> logThroughput(messagesPerInterval, videoId),
                        10, 10, TimeUnit.SECONDS
                );

                // 6) Main polling loop – continues until scraperFuture completes
                while (!scraperFuture.isDone()) {
                    page.waitForTimeout(POLL_INTERVAL_MS);

                    // 1) Attempt to find the chat iframe
                    boolean isChatPresent = false;
                    try {
                        // If count == 1, then the chat iframe is present; if 0, it's gone
                        long iframeCount = page.frameLocator("iframe#chatframe").owner().count();
                        isChatPresent = (iframeCount > 0);
                    } catch (Exception e) {
                        log.warn("Error checking for chat iframe on video {}: {}", videoId, e.getMessage());
                    }

                    // 2) If the chat iframe is missing, we assume the live is over or chat is disabled
                    if (!isChatPresent) {
                        log.warn("❌ Chat iframe disappeared for video {}. Stopping scraper...", videoId);
                        scraperFuture.complete(null);  // This will end the while loop
                    }
                }

                logTask.cancel(false);
                scheduler.shutdown();
            }

            // 7) If we exit the loop normally (no exception), mark completion
            if (!scraperFuture.isCompletedExceptionally()) {
                scraperFuture.complete(null);
                state.setStatus(ScraperState.Status.COMPLETED);
                log.info("Scraper for video {} completed normally.", videoId);
            }

        } catch (PlaywrightException pwe) {
            handlePlaywrightException(videoId, pwe);
            scraperFuture.completeExceptionally(pwe);
            state.setStatus(ScraperState.Status.FAILED);
            state.setErrorMessage(parsePlaywrightError(pwe));
        } catch (Exception ex) {
            log.error("Error in scraping logic for videoId={}: {}", videoId, ex.getMessage(), ex);
            scraperFuture.completeExceptionally(ex);
            state.setStatus(ScraperState.Status.FAILED);
            state.setErrorMessage("General error: " + ex.getMessage());
        } finally {
            closePageSafely(page, videoId);
        }
    }

    /**
     * Navigates to the YouTube video page and waits for the chat iframe to appear.
     */
    private void navigateToVideo(Page page, String videoId) {
        String fullUrl = YOUTUBE_WATCH_URL + videoId;
        page.navigate(fullUrl);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        waitForChatIframe(page);
    }

    /**
     * Injects the JavaScript scripts/observers and sets up the exposed Java function
     * for handling new chat messages.
     */
    private void setupChatScripts(Page iframePage, Locator chatBodyLocator, AtomicInteger messagesPerInterval,
                                  String videoId, String videoTitle, String channelName) {
        // Expose the chat handler object
        iframePage.evaluate(YouTubeChatScriptProvider.getExposeHandlerScript());

        // Keep chat active
        iframePage.evaluate(YouTubeChatScriptProvider.getChatActivationScript());

        // Expose the Java callback to handle new messages from the observer
        iframePage.exposeFunction("_ytChatHandler_onNewMessages", (args) -> {
            String username = (String) args[0];
            String messageText = (String) args[1];
            // log.info("[{}]: {}", username, messageText);
            messagesPerInterval.addAndGet(1);

            ScraperState currentState = scraperStates.get(videoId);
            if (currentState != null) {
                currentState.getTotalMessages().incrementAndGet();
            }

            ChatMessage chatMessage = new ChatMessage(videoTitle, channelName, videoId, UUID.randomUUID().toString(), username, messageText, System.currentTimeMillis());
            chatBroadcastService.broadcast(videoId, chatMessage);

            // Profanity / keyword logic
            profanityLogService.logIfProfane(username, messageText);

            // Offload the keyword ranking update asynchronously using the executor.
            chatScraperExecutor.execute(() -> keywordRankingService.updateKeywordRanking(videoId, messageText));

            return null;
        });

        // Set up the MutationObserver to watch for new chat messages
        chatBodyLocator.evaluate(YouTubeChatScriptProvider.getMutationObserverScript());
    }

    /**
     * Logs the throughput for the last 10 seconds and updates throughput-related metrics
     * (lastThroughput, maxThroughput, and rolling average throughput) in ScraperState.
     *
     * <p>
     * The rolling average throughput is updated using the incremental average formula:
     * </p>
     *
     * <pre>
     * newAvg = (oldAvg * (intervalCount - 1) + currentThroughput) / intervalCount
     * </pre>
     *
     * <p>
     * This ensures that the average is efficiently updated without storing historical throughput values.
     * </p>
     *
     * @param messagesPerInterval The AtomicInteger tracking the message count in the last 10-second interval.
     * @param videoId             The YouTube video ID for which this metric is being updated.
     */
    private void logThroughput(AtomicInteger messagesPerInterval, String videoId) {
        int count = messagesPerInterval.getAndSet(0);
        log.info("📊 Throughput: {} messages in last 10s for video {}", count, videoId);

        ScraperState state = scraperStates.get(videoId);
        if (state != null) {
            // Update last throughput
            state.setLastThroughput(count);

            // Possibly update max throughput
            if (count > state.getMaxThroughput()) {
                state.setMaxThroughput(count);
            }

            // Increment intervals count and update rolling average
            long newIntervalsCount = state.getIntervalsCount().incrementAndGet();

            // If it's the first interval, the average is simply `count`
            if (newIntervalsCount == 1) {
                state.setAverageThroughput(count);
            } else {
                double oldAvg = state.getAverageThroughput();
                double newAvg = (oldAvg * (newIntervalsCount - 1) + count) / newIntervalsCount;
                state.setAverageThroughput(newAvg);
            }
        }
    }

    /**
     * Called in a finally block after scraping is done or fails, ensuring the page is closed gracefully.
     */
    private void closePageSafely(Page page, String videoId) {
        try {
            page.close();
        } catch (Exception closeEx) {
            log.warn("Error closing page for video {}: {}", videoId, closeEx.getMessage());
        }
    }

    /**
     * Removes the video ID from active maps and logs the cleanup of the scraper session.
     */
    private void cleanupScraper(String videoId) {
        activeFutures.remove(videoId);
        videoThreadNames.remove(videoId);
        log.info("Finished or aborted scraping session for video {}", videoId);
    }

    /**
     * Handles unexpected top-level exceptions outside the main scraping logic.
     */
    private void handleOuterException(
            String videoId,
            Exception ex,
            CompletableFuture<Void> scraperFuture,
            ScraperState state
    ) {
        log.error("Unhandled exception while scraping video {}: {}", videoId, ex.getMessage(), ex);
        scraperFuture.completeExceptionally(ex);
        state.setStatus(ScraperState.Status.FAILED);
        state.setErrorMessage("Outer error: " + ex.getMessage());
    }

    public ScraperState getScraperState(String videoId) {
        return scraperStates.get(videoId);
    }

    // ------------------------------------------------------------------------
    // Helper Methods
    // ------------------------------------------------------------------------

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

    String extractUsername(Locator messageElement) {
        return messageElement.locator("xpath=.//*[@id='author-name']").innerText();
    }

    String extractMessageText(Locator messageElement) {
        return (String) messageElement.evaluate(YouTubeChatScriptProvider.extractMessageText());
    }

    String extractVideoTitle(Page page) {
        log.debug("Extracting video title.");
        page.waitForSelector("ytd-watch-metadata",
                new Page.WaitForSelectorOptions().setTimeout(PLAYWRIGHT_TIMEOUT_MS));
        return page.evaluate(YouTubeChatScriptProvider.extractVideoTitle()).toString();
    }

    String extractChannelName(Page page) {
        log.debug("Extracting channel name.");
        return page.evaluate(YouTubeChatScriptProvider.extractChannelName()).toString();
    }

    void handlePlaywrightException(String videoId, PlaywrightException pwe) {
        if (pwe.getMessage().contains("TargetClosedError")) {
            log.warn("Target page closed unexpectedly for video {}. Restarting scraper...", videoId);
            // Optionally restart the scraper if needed.
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
