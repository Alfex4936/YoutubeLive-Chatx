package csw.youtube.chat.live.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import csw.youtube.chat.live.dto.ChatMessage;
import csw.youtube.chat.live.model.ScraperState;
import csw.youtube.chat.playwright.PlaywrightFactory;
import csw.youtube.chat.profanity.service.ProfanityLogService;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for scraping YouTube Live Chat messages for a given video ID.
 * <p>
 * This service utilizes Playwright to navigate to a YouTube Live Stream,
 * access the chat iframe, and extract chat messages in a non-blocking, asynchronous manner.
 * It also handles message deduplication and broadcasts new messages using {@link ChatBroadcastService}.
 * Profanity logging is performed asynchronously via {@link ProfanityLogService}.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class YTChatScraperService {

    public static final String YOUTUBE_WATCH_URL = "https://www.youtube.com/watch?v=";
    private static final int POLL_INTERVAL_MS = 1000;
    private static final int PLAYWRIGHT_TIMEOUT_MS = 10000; // For selectors and navigation

    @Getter
    private final Map<String, ScraperState> scraperStates = new ConcurrentHashMap<>();
    @Getter
    // Keep track of active scrapers: videoId -> future
    final Map<String, CompletableFuture<Void>> activeFutures = new ConcurrentHashMap<>();
    @Getter
    // Map of videoId -> threadName (or some stable ID) for display
    private final Map<String, String> videoThreadNames = new ConcurrentHashMap<>();


    private final ChatBroadcastService chatBroadcastService;
    private final ProfanityLogService profanityLogService;
    private final PlaywrightFactory playwrightFactory;
    private final KeywordRankingService keywordRankingService;
    /**
     * Executor for running chat scraping tasks asynchronously.
     */
    @Qualifier("chatScraperExecutor")
    private final Executor chatScraperExecutor;

    @PreDestroy // have to use this after VT
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
        if (videoId == null || videoId.trim().isEmpty()) {
            throw new IllegalArgumentException("Video ID cannot be null or empty.");
        }

        if (activeFutures.containsKey(videoId)) {
            log.warn("Scraper already running for video {}. Ignoring new request.", videoId);
            return CompletableFuture.completedFuture(null);
        }

        // Create the state object
        ScraperState state = new ScraperState(videoId);
        state.setStatus(ScraperState.Status.RUNNING);
        scraperStates.put(videoId, state);

        log.info("Starting chat scraper for video ID: {}", videoId);
        CompletableFuture<Void> scraperFuture = new CompletableFuture<>();
        activeFutures.put(videoId, scraperFuture);

        String threadName = Thread.currentThread().getName();
        videoThreadNames.put(videoId, threadName);
        state.setThreadName(threadName);

        // All logic is in this try-block so we don't need an extra runScraper() method
        try (Playwright playwright = playwrightFactory.create();
             Browser browser = launchBrowser(playwright);
             Page page = browser.newPage()) {

            // Navigate to the watch page
            String fullUrl = YOUTUBE_WATCH_URL + videoId;
            page.navigate(fullUrl);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            // Wait for the chat iframe, gather basic info
            waitForChatIframe(page);
            String videoTitle = extractVideoTitle(page);
            String channelName = extractChannelName(page);
            log.info("Video Title: '{}', Channel Name: '{}' for video {}", videoTitle, channelName, videoId);

            // Set up references to chat area
            FrameLocator chatFrameLocator = page.frameLocator("iframe#chatframe");
            Locator chatBodyLocator = chatFrameLocator.locator("body");
            Locator chatMessagesLocator = chatFrameLocator.locator("div#items yt-live-chat-text-message-renderer");
            // noinspection resource
            Page iframePage = chatBodyLocator.page(); // 기존 page 하위 객체라 안닫아도 됨
            waitForInitialMessages(chatMessagesLocator);

            // Throughput tracking variables
            AtomicInteger totalMessages = new AtomicInteger(0);   // Total messages since start
            AtomicInteger messagesPerInterval = new AtomicInteger(0); // Messages per time window

            // Expose Java function that the browser can call when new messages appear
            iframePage.evaluate("window._ytChatHandler = {}");
            iframePage.exposeFunction("_ytChatHandler_onNewMessages", _ -> {
                try {
                    int newMessages = chatMessagesLocator.count();
                    int previousCount = totalMessages.get();
                    if (newMessages > previousCount) {

                        int messagesAdded = newMessages - previousCount;
                        totalMessages.set(newMessages);
                        messagesPerInterval.addAndGet(messagesAdded);

                        processNewMessages(
                                videoId, videoTitle, channelName,
                                chatMessagesLocator,
                                previousCount
                        );
                    }
                } catch (Exception e) {
                    log.error("❌ Error processing new messages: {}", e.getMessage(), e);
                }
                return null;
            });

            // Inject a MutationObserver to call `_ytChatHandler_onNewMessages` on DOM changes
            chatBodyLocator.evaluate("""
                        () => {
                            try {
                                const chatContainer = document.querySelector('div#items');
                                if (!chatContainer) {
                                    console.error("Chat container not found");
                                    return;
                                }
                                console.log("MutationObserver started");
                    
                                // Clear any existing observer
                                if (window._chatObserver) {
                                    window._chatObserver.disconnect();
                                    console.log("Disconnected existing observer");
                                }
                    
                                const observer = new MutationObserver(mutations => {
                                    window._ytChatHandler_onNewMessages({ mutations: mutations.length });
                                });
                                observer.observe(chatContainer, { childList: true, subtree: false });
                                window._chatObserver = observer;
                                console.log("MutationObserver setup complete");
                            } catch (error) {
                                console.error("MutationObserver setup failed", error);
                            }
                        }
                    """);

            // TODO do we need resource?
            // Throughput logging: Every 10 seconds, log messages per interval
            try (ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor()) {
                ScheduledFuture<?> logTask = scheduler.scheduleAtFixedRate(() -> {
                    int count = messagesPerInterval.getAndSet(0); // Reset the counter after logging
                    log.info("📊 Throughput: {} messages processed in the last 10 seconds for video {}", count, videoId);
                }, 10, 10, TimeUnit.SECONDS); // Logs every 10 seconds


                // Keep the scraper running until the user signals it is done (scraperFuture completed)
                while (!scraperFuture.isDone()) {
                    // Let Playwright handle any pending events.
                    // MutationObserver triggers `_ytChatHandler_onNewMessages` promptly.
                    page.waitForTimeout(POLL_INTERVAL_MS);
                }

                logTask.cancel(false);
                scheduler.shutdown();
            }

            // Once we exit the loop, we assume someone else completed the future.
            if (!scraperFuture.isCompletedExceptionally()) {
                scraperFuture.complete(null);
                state.setStatus(ScraperState.Status.COMPLETED);
                log.info("Scraper for video {} completed normally.", videoId);
            }

        } catch (PlaywrightException pwe) {
            // E.g. if iframe doesn't appear in time, or no live chat
            log.error("Playwright error for videoId={}: {}", videoId, pwe.getMessage(), pwe);
            scraperFuture.completeExceptionally(pwe);
            state.setStatus(ScraperState.Status.FAILED);

            // Parse the error for a friendlier explanation:
            String userFriendlyMsg = parsePlaywrightError(pwe);
            state.setErrorMessage(userFriendlyMsg);

        } catch (Exception e) {
            log.error("Error in scraping logic for videoId={}: {}", videoId, e.getMessage(), e);
            scraperFuture.completeExceptionally(e);
            state.setStatus(ScraperState.Status.FAILED);
            state.setErrorMessage("General error: " + e.getMessage());
        } finally {
            // Clean up from the activeScrapers map
            activeFutures.remove(videoId);
            videoThreadNames.remove(videoId);
            log.info("Finished or aborted scraping session for video {}", videoId);
        }

        // Return the future so the caller can check status or cancel externally
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
        // Forcibly interrupt the thread if needed (currently CompletableFuture.complete(null) just signals completion)
        future.complete(null); // Signal to stop, actual thread interruption/resource release needs to be handled in runScraper if needed.

        ScraperState state = scraperStates.get(videoId);
        if (state != null) {
            state.setStatus(ScraperState.Status.COMPLETED);
            state.setErrorMessage("Stopped by user.");
        }
        log.info("Stopping scraper requested for video ID: {}", videoId);
        return "Stopping scraper for video ID: " + videoId;
    }

    Browser launchBrowser(Playwright playwright) {
        log.debug("Launching browser instance.");
        return playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(true) // Run headless in production
                        .setArgs(List.of(
                                "--no-sandbox", // Required for Docker/non-root user
                                "--disable-extensions", // Disable extensions for performance
                                "--disable-gpu", // Disable GPU acceleration if not needed
                                "--disable-dev-shm-usage", //  May help with memory issues in containers
                                "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36" // Mimic a real browser
                        ))
        );
    }

    private void waitForChatIframe(Page page) {
        log.debug("Waiting for chat iframe to appear.");
        page.waitForSelector("iframe#chatframe", new Page.WaitForSelectorOptions().setTimeout(PLAYWRIGHT_TIMEOUT_MS));
    }

    private void waitForInitialMessages(Locator chatMessagesLocator) {
        log.debug("Waiting for initial chat messages to load.");
        chatMessagesLocator.first().waitFor(new Locator.WaitForOptions().setTimeout(PLAYWRIGHT_TIMEOUT_MS));
    }


    void processNewMessages(String videoId, String videoTitle, String channelName, Locator chatMessagesLocator, int processedMessageCount) {
        int currentMessageCount = chatMessagesLocator.count();
        if (currentMessageCount <= processedMessageCount) {
            return; // No new messages
        }

        // log.debug("Processing new messages from index {} to {}", processedMessageCount, currentMessageCount - 1);
        for (int i = processedMessageCount; i < currentMessageCount; i++) {
            processSingleMessage(videoId, videoTitle, channelName, chatMessagesLocator.nth(i));
        }
    }


    void processSingleMessage(String videoId, String videoTitle, String channelName, Locator messageElement) {
        try {
            String username = extractUsername(messageElement);
            String messageText = extractMessageText(messageElement);
            // String stableKey = buildStableKey(username, messageText);
            log.debug("{}: {}", username, messageText);

//            if (messageCache.getIfPresent(stableKey) != null) {
//                // log.debug("Duplicate message detected, skipping. Key: {}", stableKey);
//                return; // Skip duplicate message
//            }
//            messageCache.put(stableKey, Boolean.TRUE); // Mark message as processed

            ChatMessage chatMessage = new ChatMessage(videoTitle, channelName, videoId, UUID.randomUUID().toString(), username, messageText, System.currentTimeMillis());
            chatBroadcastService.broadcast(videoId, chatMessage); // Broadcast to subscribers
            profanityLogService.logIfProfane(username, messageText); // Asynchronous profanity logging

            // Update keyword ranking for the processed message.
            // TODO: async?
            keywordRankingService.updateKeywordRanking(videoId, messageText);
        } catch (Exception e) {
            log.error("Error processing a single chat message for video {}: {}", videoId, e.getMessage(), e);
        }
    }

    String extractUsername(Locator messageElement) {
        return messageElement.locator("xpath=.//*[@id='author-name']").innerText();
    }

    String extractMessageText(Locator messageElement) {
        return (String) messageElement.evaluate("""
                    (el) => {
                        // Get the message container from shadow DOM if available
                        const container = el.shadowRoot 
                            ? el.shadowRoot.querySelector('#message') 
                            : el.querySelector('#message');
                        if (!container) return '';
                
                        // Use XPath to get all descendant nodes in order
                        const xpathResult = document.evaluate(
                            './/node()', container, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null
                        );
                
                        let result = '';
                        for (let i = 0; i < xpathResult.snapshotLength; i++) {
                            const node = xpathResult.snapshotItem(i);
                            if (node.nodeType === Node.TEXT_NODE) {
                                result += node.textContent;
                            } else if (node.nodeType === Node.ELEMENT_NODE && node.tagName.toLowerCase() === 'img') {
                                // Prefer shared-tooltip-text (which is often formatted like :emoji:) if available
                                const emoji = node.getAttribute('shared-tooltip-text') || node.getAttribute('alt') || '';
                                result += emoji;
                            }
                        }
                        return result;
                    }
                """);
    }

    String extractVideoTitle(Page page) {
        log.debug("Extracting video title.");
        page.waitForSelector("ytd-watch-metadata", new Page.WaitForSelectorOptions().setTimeout(PLAYWRIGHT_TIMEOUT_MS));
        return page.evaluate("""
                () => {
                  const watchMeta = document.querySelector("ytd-watch-metadata");
                  if (!watchMeta) return "";
                  const titleEl = watchMeta.querySelector("#title h1 yt-formatted-string");
                  return titleEl ? titleEl.textContent.trim() : "";
                }
                """).toString();
    }

    String extractChannelName(Page page) {
        log.debug("Extracting channel name.");
        return page.evaluate("""
                () => {
                  const watchMeta = document.querySelector("ytd-watch-metadata");
                  if (!watchMeta) return "";
                  const ownerEl = watchMeta.querySelector("#owner ytd-video-owner-renderer");
                  if (!ownerEl) return "";
                  const channelLink = ownerEl.querySelector("#channel-name #container #text-container yt-formatted-string a");
                  return channelLink ? channelLink.textContent.trim() : "";
                }
                """).toString();
    }

    /**
     * Builds a stable key for message deduplication based on username, message text, and the current minute.
     * This ensures that if the same user posts the same message within the same minute, it is considered a duplicate.
     *
     * @param username    The username of the chat message sender.
     * @param messageText The text content of the chat message.
     * @return A stable key (hexadecimal hash) for message deduplication.
     */
    String buildStableKey(String username, String messageText) {
        long minuteStamp = Instant.now().getEpochSecond() / 60;  // Current minute
        String raw = username + "|" + messageText + "|" + minuteStamp;
        return Integer.toHexString(raw.hashCode()); // Simple hash for deduplication
    }

    void handlePlaywrightException(String videoId, PlaywrightException pwe) {
        if (pwe.getMessage().contains("TargetClosedError")) {
            log.warn("Target page closed unexpectedly for video {}. Restarting scraper...", videoId);
            // Consider restarting the scraper here if robustness is critical.
            // For now, let it complete and a new scrape request will restart it if needed.
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
        // Add more cases as needed...

        // Fallback if none of the above patterns match:
        return "Playwright error: " + rawMsg;
    }

}