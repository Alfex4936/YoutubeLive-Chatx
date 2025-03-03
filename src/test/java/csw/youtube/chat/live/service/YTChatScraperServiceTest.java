package csw.youtube.chat.live.service;


import com.microsoft.playwright.*;
import csw.youtube.chat.playwright.PlaywrightFactory;
import csw.youtube.chat.profanity.service.ProfanityLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class YTChatScraperServiceTest {

    private ExecutorService executorService;
    private ChatBroadcastService chatBroadcastService;
    private ProfanityLogService profanityLogService;
    private PlaywrightFactory playwrightFactory;
    private KeywordRankingService keywordRankingService;

    @BeforeEach
    public void setup() {
        // Use an executor service so that runScraper runs in a background thread
        executorService = Executors.newCachedThreadPool();

        // Create mocks for dependencies that are not used in our test logic.
        chatBroadcastService = Mockito.mock(ChatBroadcastService.class);
        profanityLogService = Mockito.mock(ProfanityLogService.class);
        playwrightFactory = Mockito.mock(PlaywrightFactory.class);
        keywordRankingService = Mockito.mock(KeywordRankingService.class);
    }

    @AfterEach
    public void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    public void testConcurrentScraperSingleVideo() throws Exception {
        // Latch to simulate the long-running nature of runScraper.
        CountDownLatch finishLatch = new CountDownLatch(1);
        // Latch to know when runScraper has been entered.
        CountDownLatch startedLatch = new CountDownLatch(1);

        TestYTChatScraperService service = new TestYTChatScraperService(
                chatBroadcastService,
                profanityLogService,
                playwrightFactory,
                keywordRankingService,
                executorService,
                finishLatch,
                startedLatch
        );

        String videoId = "testVideo123";

        // Start the first scraper; it will block on finishLatch.await()
        CompletableFuture<Void> future1 = service.scrapeChannel(videoId);

        // Wait until runScraper is entered.
        assertTrue(startedLatch.await(2, TimeUnit.SECONDS), "Scraper did not start in time.");

        // Concurrently call scrapeChannel again for the same videoId.
        CompletableFuture<Void> future2 = service.scrapeChannel(videoId);

        // Since a scraper is already running, the second call should immediately return a completed future.
        assertTrue(future2.isDone(), "Second scrapeChannel call should return a completed future as scraper is already running.");

        // Now let the first scraper finish.
        finishLatch.countDown();
        // Wait for the first future to complete.
        future1.get(2, TimeUnit.SECONDS);

        // After completion, the activeScrapers map should no longer contain the videoId.
        assertFalse(service.activeFutures.containsKey(videoId),
                "Active scrapers map should not contain the videoId after the scraper completes.");
    }

    @Test
    public void testMultipleScrapersConcurrent() throws Exception {
        int scraperCount = 3;
        CountDownLatch startedLatch = new CountDownLatch(scraperCount);
        CountDownLatch finishLatch = new CountDownLatch(1);

        // Create a single TestYTChatScraperService instance that uses the common latches.
        TestYTChatScraperService service = new TestYTChatScraperService(
                chatBroadcastService,
                profanityLogService,
                playwrightFactory,
                keywordRankingService,
                executorService,
                finishLatch,
                startedLatch
        );

        // Define multiple video IDs.
        String[] videoIds = {"video1", "video2", "video3"};
        var futures = new CompletableFuture[videoIds.length];

        // Start a scraper for each video ID.
        for (int i = 0; i < videoIds.length; i++) {
            futures[i] = service.scrapeChannel(videoIds[i]);
        }

        // Wait until all scrapers have started (i.e. runScraper has been entered for each).
        assertTrue(startedLatch.await(2, TimeUnit.SECONDS), "Not all scrapers started in time.");

        // Verify that the activeScrapers map contains all three scrapers.
        assertEquals(scraperCount, service.activeFutures.size(), "Active scrapers map should contain all scrapers.");

        // Release all scrapers so they can complete.
        finishLatch.countDown();

        // Wait for all futures to complete.
        CompletableFuture.allOf(futures).get(2, TimeUnit.SECONDS);

        // After all scrapers have finished, the activeScrapers map should be empty.
        assertTrue(service.activeFutures.isEmpty(), "Active scrapers map should be empty after all scrapers finish.");
    }

    @Test
    public void testStopScraperNoActive() {
        // Use TestableYTChatScraperService to test stopScraper
        TestableYTChatScraperService service = new TestableYTChatScraperService(
                chatBroadcastService, profanityLogService, playwrightFactory, keywordRankingService, executorService
        );
        String videoId = "nonexistent";
        String result = service.stopScraper(videoId);
        assertEquals("No active scraper found for video ID: " + videoId, result);
    }

    @Test
    public void testStopScraperWithActive() {
        // Use TestableYTChatScraperService to test stopScraper when a scraper is active
        TestableYTChatScraperService service = new TestableYTChatScraperService(
                chatBroadcastService, profanityLogService, playwrightFactory, keywordRankingService, executorService
        );
        String videoId = "activeVideo";

        // Create an active scraper future and put it into the activeScrapers map.
        CompletableFuture<Void> future = new CompletableFuture<>();
        service.activeFutures.put(videoId, future);

        String result = service.stopScraper(videoId);
        assertEquals("Stopping scraper for video ID: " + videoId, result);
        // The active scraper should be removed.
        assertFalse(service.activeFutures.containsKey(videoId));
        // The future should be completed.
        assertTrue(future.isDone());
    }

    @Test
    public void testLaunchBrowser() {
        // Create mocks for Playwright, BrowserType, and Browser.
        Playwright mockPlaywright = Mockito.mock(Playwright.class);
        BrowserType mockBrowserType = Mockito.mock(BrowserType.class);
        Browser mockBrowser = Mockito.mock(Browser.class);

        // Stub the call chain: playwright.chromium() -> BrowserType and launch(...) -> Browser.
        Mockito.when(mockPlaywright.chromium()).thenReturn(mockBrowserType);
        Mockito.when(mockBrowserType.launch(Mockito.any())).thenReturn(mockBrowser);

        TestableYTChatScraperService service = new TestableYTChatScraperService(
                chatBroadcastService, profanityLogService, playwrightFactory, keywordRankingService, executorService
        );

        Browser resultBrowser = service.testLaunchBrowser(mockPlaywright);
        assertNotNull(resultBrowser, "launchBrowser should return a Browser instance.");
        assertEquals(mockBrowser, resultBrowser, "The returned Browser instance should match the mocked Browser.");
    }

    /****** EXTRACTIONS *******/
    @Test
    public void testExtractUsername() {
        // Create a mock Locator and stub evaluate to return a test username.
        Locator mockLocator = Mockito.mock(Locator.class);
        Mockito.when(mockLocator.evaluate(Mockito.anyString())).thenReturn("TestUser");

        TestableYTChatScraperService service = new TestableYTChatScraperService(
                chatBroadcastService, profanityLogService, playwrightFactory, keywordRankingService, executorService
        );

        String username = service.testExtractUsername(mockLocator);
        assertEquals("TestUser", username);
    }

    @Test
    public void testExtractMessageText() {
        // Create a mock Locator and stub evaluate to return a test message text.
        Locator mockLocator = Mockito.mock(Locator.class);
        Mockito.when(mockLocator.evaluate(Mockito.anyString())).thenReturn("Hello, world!");

        TestableYTChatScraperService service = new TestableYTChatScraperService(
                chatBroadcastService, profanityLogService, playwrightFactory, keywordRankingService, executorService
        );

        String messageText = service.testExtractMessageText(mockLocator);
        assertEquals("Hello, world!", messageText);
    }

    @Test
    public void testExtractVideoTitle() {
        // Create a mock Page and stub waitForSelector and evaluate.
        Page mockPage = Mockito.mock(Page.class);
        Mockito.when(mockPage.waitForSelector(Mockito.anyString(), Mockito.any()))
                .thenReturn(null);
        Mockito.when(mockPage.evaluate(Mockito.anyString()))
                .thenReturn("Test Video Title");

        TestableYTChatScraperService service = new TestableYTChatScraperService(
                chatBroadcastService, profanityLogService, playwrightFactory, keywordRankingService, executorService
        );

        String title = service.testExtractVideoTitle(mockPage);
        assertEquals("Test Video Title", title);
    }

    @Test
    public void testExtractChannelName() {
        // Create a mock Page and stub evaluate to return a test channel name.
        Page mockPage = Mockito.mock(Page.class);
        Mockito.when(mockPage.evaluate(Mockito.anyString())).thenReturn("Test Channel");

        TestableYTChatScraperService service = new TestableYTChatScraperService(
                chatBroadcastService, profanityLogService, playwrightFactory, keywordRankingService, executorService
        );

        String channelName = service.testExtractChannelName(mockPage);
        assertEquals("Test Channel", channelName);
    }

    @Test
    public void testBuildStableKey() {
        TestableYTChatScraperService service = new TestableYTChatScraperService(
                chatBroadcastService, profanityLogService, playwrightFactory, keywordRankingService, executorService
        );

        String username = "user";
        String message = "Hello, world!";
        String key1 = service.testBuildStableKey(username, message);
        String key2 = service.testBuildStableKey(username, message);

        assertNotNull(key1, "The generated key should not be null.");
        assertFalse(key1.isEmpty(), "The generated key should not be empty.");
        // If called within the same minute, the keys should be identical.
        assertEquals(key1, key2, "The keys should be identical for the same input within the same minute.");

        // A different message should produce a different key.
        String keyDifferent = service.testBuildStableKey(username, "Another message");
        assertNotEquals(key1, keyDifferent, "Different message texts should produce different keys.");
    }

    /**
     * A test subclass that overrides runScraper to simulate a long-running task.
     * CountDownLatch to block and then release the scraper.
     */
    static class TestYTChatScraperService extends YTChatScraperService {
        private final CountDownLatch finishLatch;
        private final CountDownLatch startedLatch;

        public TestYTChatScraperService(ChatBroadcastService chatBroadcastService,
                                        ProfanityLogService profanityLogService,
                                        PlaywrightFactory playwrightFactory,
                                        KeywordRankingService keywordRankingService,
                                        Executor chatScraperExecutor,
                                        CountDownLatch finishLatch,
                                        CountDownLatch startedLatch) {
            super(chatBroadcastService, profanityLogService, playwrightFactory, keywordRankingService, chatScraperExecutor);
            this.finishLatch = finishLatch;
            this.startedLatch = startedLatch;
        }

        @Override
        void runScraper(String videoId) {
            // Signal that runScraper has started.
            startedLatch.countDown();
            try {
                // Block until the latch is released (simulate long-running process)
                finishLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * A test subclass to expose the extractor functions and the key builder.
     */
    static class TestableYTChatScraperService extends YTChatScraperService {

        public TestableYTChatScraperService(ChatBroadcastService chatBroadcastService,
                                            ProfanityLogService profanityLogService,
                                            PlaywrightFactory playwrightFactory,
                                            KeywordRankingService keywordRankingService,
                                            Executor chatScraperExecutor) {
            super(chatBroadcastService, profanityLogService, playwrightFactory, keywordRankingService, chatScraperExecutor);
        }

        public String testExtractUsername(Locator locator) {
            return extractUsername(locator);
        }

        public String testExtractMessageText(Locator locator) {
            return extractMessageText(locator);
        }

        public String testExtractVideoTitle(Page page) {
            return extractVideoTitle(page);
        }

        public String testExtractChannelName(Page page) {
            return extractChannelName(page);
        }

        public String testBuildStableKey(String username, String messageText) {
            return buildStableKey(username, messageText);
        }

        public Browser testLaunchBrowser(Playwright playwright) {
            return launchBrowser(playwright);
        }
    }
}