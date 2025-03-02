package csw.youtube.chat.live.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import csw.youtube.chat.live.dto.ChatMessage;
import csw.youtube.chat.playwright.PlaywrightFactory;
import csw.youtube.chat.profanity.service.ProfanityLogService;
import jakarta.annotation.PreDestroy;
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
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final int MESSAGE_CACHE_MAX_SIZE = 5_000;
    private static final int MESSAGE_CACHE_EXPIRY_MINUTES = 10;
    private static final int POLL_INTERVAL_MS = 1000;
    private static final int PLAYWRIGHT_TIMEOUT_MS = 15000; // For selectors and navigation
    /**
     * Tracks active scrapers by videoId.
     * The value is a {@link CompletableFuture} that represents the running scraper task.
     * Using ConcurrentHashMap for thread-safe access.
     */
    final Map<String, CompletableFuture<Void>> activeScrapers = new ConcurrentHashMap<>();
    /**
     * For deduplication of processed messages, we store one Guava Cache per videoId.
     * - Maximum {@value MESSAGE_CACHE_MAX_SIZE} entries
     * - Evict after {@value MESSAGE_CACHE_EXPIRY_MINUTES} minutes
     * Using ConcurrentHashMap for thread-safe access to caches.
     */
    final Map<String, Cache<String, Boolean>> perVideoMessageCache = new ConcurrentHashMap<>();
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
        activeScrapers.keySet().forEach(this::stopScraper);
    }

    /**
     * Returns or creates an LRU+time-based cache for the given videoId.
     * This cache is used to deduplicate messages and prevent reprocessing.
     *
     * @param videoId The YouTube video ID.
     * @return The message cache for the given videoId.
     */
    Cache<String, Boolean> getMessageCache(String videoId) {
        return perVideoMessageCache.computeIfAbsent(videoId, _ ->
                CacheBuilder.newBuilder()
                        .maximumSize(MESSAGE_CACHE_MAX_SIZE)
                        .expireAfterWrite(MESSAGE_CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
                        .build()
        );
    }

    /**
     * Starts scraping chat messages for a given YouTube video ID if a scraper is not already active.
     * This method is asynchronous and returns a {@link CompletableFuture} representing the scraping task.
     * If a scraper is already running for the given video ID, it will log a warning and return a completed future.
     *
     * @param videoId The YouTube video ID to scrape.
     * @return A {@link CompletableFuture} that completes when the scraper finishes or if it's already running.
     * The future does not hold any meaningful value upon completion.
     * @throws IllegalArgumentException if the videoId is null or empty.
     */
    @Async("chatScraperExecutor")
    public CompletableFuture<Void> scrapeChannel(String videoId) {
        if (videoId.trim().isEmpty()) {
            throw new IllegalArgumentException("Video ID cannot be null or empty.");
        }

        if (activeScrapers.containsKey(videoId)) {
            log.warn("Scraper already running for video {}. Ignoring new request.", videoId);
            return CompletableFuture.completedFuture(null); // Already running, nothing to do
        }

        log.info("Starting chat scraper for video ID: {}", videoId);
        CompletableFuture<Void> scraperFuture = new CompletableFuture<>();
        activeScrapers.put(videoId, scraperFuture);

        log.info("[Thread: {}] Starting scrapeChannel for video ID: {}", Thread.currentThread().getName(), videoId); // Thread log - scrapeChannel

        // Run the scraper logic asynchronously using the configured executor
        CompletableFuture.runAsync(() -> {
                    log.info("[Thread: {}] Running runScraper asynchronously for video ID: {}", Thread.currentThread().getName(), videoId); // Thread log - runScraper async start
                    runScraper(videoId);
                    log.info("[Thread: {}] runScraper completed asynchronously for video ID: {}", Thread.currentThread().getName(), videoId); // Thread log - runScraper async end
                }, chatScraperExecutor)
                .whenComplete((res, ex) -> {
                    activeScrapers.remove(videoId); // Clean up active scraper map
                    if (ex != null) {
                        log.error("Scraper for video {} completed with error: {}", videoId, ex.getMessage(), ex);
                        scraperFuture.completeExceptionally(ex); // Propagate exception to the future
                    } else {
                        log.info("Scraper for video {} completed successfully.", videoId);
                        scraperFuture.complete(null); // Complete normally
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
        CompletableFuture<Void> future = activeScrapers.remove(videoId);
        if (future == null) {
            return "No active scraper found for video ID: " + videoId;
        }
        // Forcibly interrupt the thread if needed (currently CompletableFuture.complete(null) just signals completion)
        future.complete(null); // Signal to stop, actual thread interruption/resource release needs to be handled in runScraper if needed.
        log.info("Stopping scraper requested for video ID: {}", videoId);
        return "Stopping scraper for video ID: " + videoId;
    }


    /**
     * Core logic for running the chat scraper for a specific video ID.
     * This method is intended to be executed asynchronously.
     * It uses Playwright to navigate to the YouTube Live Chat, extract messages,
     * handle deduplication, and broadcast new messages.
     *
     * @param videoId The YouTube video ID to scrape.
     */
    void runScraper(String videoId) {
        log.info("[Thread: {}] Executing runScraper for video {}", Thread.currentThread().getName(), videoId);
        String fullUrl = YOUTUBE_WATCH_URL + videoId;

        try (Playwright playwright = playwrightFactory.create()) {
            Browser browser = launchBrowser(playwright);
            try (browser; Page page = browser.newPage()) {
                page.navigate(fullUrl);
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);

                waitForChatIframe(page);

                String videoTitle = extractVideoTitle(page);
                String channelName = extractChannelName(page);
                log.info("Video Title: '{}', Channel Name: '{}' for video ID: {}", videoTitle, channelName, videoId);

                FrameLocator chatFrameLocator = page.frameLocator("iframe#chatframe");
                Locator chatBodyLocator = chatFrameLocator.locator("body");
                Locator chatMessagesLocator = chatFrameLocator.locator("div#items yt-live-chat-text-message-renderer");
                Page iframePage = chatBodyLocator.page();
                waitForInitialMessages(chatMessagesLocator);

                Cache<String, Boolean> messageCache = getMessageCache(videoId);
                AtomicInteger lastProcessed = new AtomicInteger(chatMessagesLocator.count());

                // Create a unique namespace to avoid conflicts
                iframePage.evaluate("window._ytChatHandler = {}");

                // Expose the function with a clear name
                iframePage.exposeFunction("_ytChatHandler_onNewMessages", args -> {
                    try {
                        int newCount = chatMessagesLocator.count();
                        int previousCount = lastProcessed.get();
                        if (newCount > previousCount) {
                            processNewMessages(videoId, videoTitle, channelName, chatMessagesLocator, messageCache, previousCount);
                            lastProcessed.set(newCount);
                        }

                    } catch (Exception e) {
                        log.error("❌ Error processing new messages: {}", e.getMessage(), e);
                    }

                    return null;
                });

                // Inject a MutationObserver in the chat iframe to detect new messages
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
                                console.log("Observer detected mutations:", mutations.length);
                                window._ytChatHandler_onNewMessages({mutations: mutations.length});
//                                    .then(() => console.log("Handler completed via observer"))
//                                    .catch(e => console.error("Observer handler error:", e));
                            });
                
                            observer.observe(chatContainer, { childList: true, subtree: false });
                            window._chatObserver = observer;
                            console.log("MutationObserver setup complete");
                        } catch (error) {
                            console.error("MutationObserver setup failed", error);
                        }
                    }
                """);

                // Get the scraper future from activeScrapers map
                CompletableFuture<Void> scraperFuture = activeScrapers.get(videoId);

                // Set up a cancellable polling scheduler using Playwright's own timing mechanisms
                AtomicBoolean isRunning = new AtomicBoolean(true);

                // Create a separate thread for the polling mechanism
                Thread pollingThread = new Thread(() -> {
                    try {
                        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                            // Check if we've been asked to stop
                            if (scraperFuture != null && scraperFuture.isDone()) {
                                log.info("Scraper future completed, stopping polling loop");
                                break;
                            }

                            // Use Playwright's own waiting mechanism for timing
                            page.waitForTimeout(1000);

                            // Poll for new messages as a backup mechanism
                            if (!isRunning.get() || Thread.currentThread().isInterrupted()) {
                                break;
                            }

                            int currentCount = chatMessagesLocator.count();
                            int previousCount = lastProcessed.get();

                            if (currentCount > previousCount) {
                                processNewMessages(videoId, videoTitle, channelName, chatMessagesLocator, messageCache, previousCount);
                                lastProcessed.set(currentCount);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error in polling thread: {}", e.getMessage(), e);
                    }
                    log.info("Polling thread exited for video {}", videoId);
                }, "chat-poller-" + videoId);

                pollingThread.setDaemon(true);
                pollingThread.start();

                // Wait for the scraper future to complete or be cancelled
                try {
                    if (scraperFuture != null) {
                        scraperFuture.get();
                    } else {
                        // If no future is provided, just wait indefinitely
                        // This is primarily for testing purposes
                        Object lock = new Object();
                        synchronized (lock) {
                            lock.wait();
                        }
                    }
                } catch (InterruptedException | ExecutionException e) {
                    log.info("Scraper waiting interrupted: {}", e.getMessage());
                } finally {
                    // Clean up
                    isRunning.set(false);
                    pollingThread.interrupt();
                    try {
                        pollingThread.join(1000);  // Wait up to 1 second for thread to exit
                    } catch (InterruptedException ignored) {
                        // Ignore
                    }

                    try {
                        page.evaluate("() => { if (window._chatObserver) window._chatObserver.disconnect(); }");
                        log.info("MutationObserver disconnected");
                    } catch (Exception e) {
                        log.warn("Error disconnecting observer: {}", e.getMessage());
                    }
                }

                log.info("Scraper loop completed for video {}", videoId);
                log.info("[Thread: {}] Scraper loop completed for video {}", Thread.currentThread().getName(), videoId);

            } catch (PlaywrightException pwe) {
                handlePlaywrightException(videoId, pwe);
            } catch (Exception e) {
                handleGeneralException(videoId, e);
            }
            log.info("Scraper finished for video {}", videoId);
            log.info("[Thread: {}] Scraper finished for video {}", Thread.currentThread().getName(), videoId);

        } catch (Exception topLevelException) {
            log.error("Scraper setup failed for video {}: {}", videoId, topLevelException.getMessage(), topLevelException);
        }
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


    void processNewMessages(String videoId, String videoTitle, String channelName, Locator chatMessagesLocator, Cache<String, Boolean> messageCache, int processedMessageCount) {
        int currentMessageCount = chatMessagesLocator.count();
        if (currentMessageCount <= processedMessageCount) {
            return; // No new messages
        }

        // log.debug("Processing new messages from index {} to {}", processedMessageCount, currentMessageCount - 1);
        for (int i = processedMessageCount; i < currentMessageCount; i++) {
            processSingleMessage(videoId, videoTitle, channelName, chatMessagesLocator.nth(i), messageCache);
        }
    }


    void processSingleMessage(String videoId, String videoTitle, String channelName, Locator messageElement, Cache<String, Boolean> messageCache) {
        try {
            String username = extractUsername(messageElement);
            String messageText = extractMessageText(messageElement);
            String stableKey = buildStableKey(username, messageText);
            log.debug("{}: {}", username, messageText);

            if (messageCache.getIfPresent(stableKey) != null) {
                // log.debug("Duplicate message detected, skipping. Key: {}", stableKey);
                return; // Skip duplicate message
            }
            messageCache.put(stableKey, Boolean.TRUE); // Mark message as processed

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

    void handleInterruptedException(String videoId, InterruptedException ie) {
        log.info("Scraper thread interrupted for video {}. Shutting down gracefully.", videoId);
        // Cleanup if necessary (resources are managed by try-with-resources)
    }

    void handleGeneralException(String videoId, Exception e) {
        log.error("Error while processing chat messages for video {}: {}", videoId, e.getMessage(), e);
    }

}