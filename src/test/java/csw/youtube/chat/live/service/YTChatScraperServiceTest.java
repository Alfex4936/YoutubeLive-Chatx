//package csw.youtube.chat.live.service;
//
//import com.microsoft.playwright.*;
//import com.microsoft.playwright.options.LoadState;
//import csw.youtube.chat.live.dto.ChatMessage;
//import csw.youtube.chat.live.model.ScraperState;
//import csw.youtube.chat.profanity.service.ProfanityLogService;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.lang.reflect.Method;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.Executor;
//import java.util.function.Consumer;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.Mockito.*;
//
///**
// * Example JUnit 5 Test for YTChatScraperService.
// */
//@ExtendWith(MockitoExtension.class)
//class YTChatScraperServiceTest {
//
//    @Mock
//    private PlaywrightBrowserPool mockPool;
//    @Mock
//    private ChatBroadcastService mockBroadcastService;
//    @Mock
//    private ProfanityLogService mockProfanityService;
//    @Mock
//    private KeywordRankingService mockKeywordRankingService;
//
//    /**
//     * The class under test.
//     */
//    @InjectMocks
//    private YTChatScraperService scraperService;
//
//    /**
//     * By default, the fields with @Getter in YTChatScraperService are final,
//     * so you should inject them via the constructor. We'll let Mockito handle that
//     * but also we can set them manually as needed.
//     */
//
//    @BeforeEach
//    void setUp() {
//        /**
//         * We can use a direct Executor that runs tasks on the same thread so our test doesn't return immediately.
//         * That ensures we can verify the results synchronously.
//         */
//        Executor singleThreadExecutor = Runnable::run;  // A simple executor that runs on calling thread
//        // We can override the actual executor in the service if it's not final. If it's final,
//        // you might need a small refactor or constructor injection with a different bean in tests.
//
//        // If using @Qualifier("chatScraperExecutor"), you might need some test config. For simplicity, do:
//        // The easiest approach is to reflect or to have a setter. For example:
//        // scraperService.setChatScraperExecutor(singleThreadExecutor);  // If there's a setter
//        // Or we forcibly set the field with reflection if it's private final. We'll do a quick hack:
//        TestUtils.setField(scraperService, "chatScraperExecutor", singleThreadExecutor);
//    }
//
//    @Test
//    void testOnDestroy_stopsAllScrapers() {
//        // Suppose we have 2 active futures
//        CompletableFuture<Void> future1 = new CompletableFuture<>();
//        CompletableFuture<Void> future2 = new CompletableFuture<>();
//        scraperService.getActiveFutures().put("vid1", future1);
//        scraperService.getActiveFutures().put("vid2", future2);
//
//        // When we call onDestroy, it should call stopScraper() on each
//        scraperService.onDestroy();
//
//        assertTrue(future1.isDone());
//        assertTrue(future2.isDone());
//    }
//
//    @Test
//    void testScrapeChannel_invalidVideoId() {
//        // Should throw an IllegalArgumentException if videoId is empty
//        assertThrows(IllegalArgumentException.class, () -> scraperService.scrapeChannel(null));
//        assertThrows(IllegalArgumentException.class, () -> scraperService.scrapeChannel("   "));
//    }
//
//    @Test
//    void testScrapeChannel_alreadyRunning() {
//        // If a future is already active for "abcd1234", return a completedFuture
//        scraperService.getActiveFutures().put("abcd1234", new CompletableFuture<>());
//
//        CompletableFuture<Void> result = scraperService.scrapeChannel("abcd1234");
//        assertTrue(result.isDone());
//        assertFalse(result.isCompletedExceptionally());
//    }
//
//    @Test
//    void testWaitForChatIframe() throws Exception {
//        Page mockPage = mock(Page.class);
//        ElementHandle mockHandle = mock(ElementHandle.class);
//        when(mockPage.waitForSelector(eq("iframe#chatframe"), any(Page.WaitForSelectorOptions.class)))
//                .thenReturn(mockHandle);
//
//        Method method = YTChatScraperService.class.getDeclaredMethod("waitForChatIframe", Page.class);
//        method.setAccessible(true);
//        method.invoke(scraperService, mockPage);
//
//        verify(mockPage).waitForSelector(eq("iframe#chatframe"), any(Page.WaitForSelectorOptions.class));
//    }
//
//    @Test
//    void testWaitForInitialMessages() throws Exception {
//        Locator mockChatMessagesLocator = mock(Locator.class);
//        Locator mockFirstLocator = mock(Locator.class);
//
//        when(mockChatMessagesLocator.first()).thenReturn(mockFirstLocator);
//        doNothing().when(mockFirstLocator).waitFor(any(Locator.WaitForOptions.class));
//
//        Method method = YTChatScraperService.class.getDeclaredMethod("waitForInitialMessages", Locator.class);
//        method.setAccessible(true);
//        method.invoke(scraperService, mockChatMessagesLocator);
//
//        verify(mockChatMessagesLocator).first();
//        verify(mockFirstLocator).waitFor(any(Locator.WaitForOptions.class));
//    }
//
//
//    /**
//     * TODO make it work.
//     * This test covers the main scraping logic.
//     * We'll mock "withBrowser" to simulate behavior, and we won't let it throw an exception
//     * so we get a normal path coverage.
//     */
//    @Test
//    void testScrapeChannel_successPath_noExceptions() {
//        // 1) Stub the "withBrowser(Consumer<Browser>)" so we can intercept the Browser consumer
//        doAnswer(invocation -> {
//            Consumer<Browser> browserConsumer = invocation.getArgument(0);
//
//            // 2) Create mocks for Browser, Page, Response, FrameLocator, etc.
//            Browser mockBrowser = mock(Browser.class);
//            Page mockPage = mock(Page.class);
//            Response mockResponse = mock(Response.class);
//            ElementHandle mockElementHandle = mock(ElementHandle.class);
//            FrameLocator mockFrameLocator = mock(FrameLocator.class);
//            Locator mockBodyLocator = mock(Locator.class);
//            Locator mockChatMessagesLocator = mock(Locator.class);
//            Locator mockFirstLocator = mock(Locator.class);
//            Page mockIframePage = mock(Page.class);
//
//            // 3) Stub out the typical calls in the main logic:
//            doNothing().when(mockPage).waitForTimeout(eq(1000.0));
//
//            // page.navigate(...) -> returns Response
//            when(mockPage.navigate(anyString())).thenReturn(mockResponse);
//
//            // page.waitForLoadState(...) -> returns void
//            doNothing().when(mockPage).waitForLoadState(LoadState.DOMCONTENTLOADED);
//
//            // The scraper calls waitForSelector for "iframe#chatframe" -> returns an ElementHandle (or null).
//            when(mockPage.waitForSelector(eq("iframe#chatframe"), any(Page.WaitForSelectorOptions.class)))
//                    .thenReturn(mockElementHandle);
//
//            // The scraper calls waitForSelector for "ytd-watch-metadata" in extractVideoTitle()
//            when(mockPage.waitForSelector(eq("ytd-watch-metadata"), any(Page.WaitForSelectorOptions.class)))
//                    .thenReturn(mockElementHandle);
//
//            // page.evaluate(...) in extractVideoTitle(...) or extractChannelName(...)
//            // The code calls this multiple times – first for title, then channel name.
//            // If you need them to return different strings, you can chain:
//            when(mockPage.evaluate(anyString()))
//                    .thenReturn("My Mocked Video Title")
//                    .thenReturn("My Mocked Channel");
//
//            // The scraper calls page.frameLocator("iframe#chatframe")
//            when(mockPage.frameLocator("iframe#chatframe")).thenReturn(mockFrameLocator);
//
//            // Then calls locator("body"), locator("div#items yt-live-chat-text-message-renderer"), etc.
//            // We'll do it step by step:
//            when(mockFrameLocator.locator("body")).thenReturn(mockBodyLocator);
//            when(mockFrameLocator.locator("div#items yt-live-chat-text-message-renderer")).thenReturn(mockChatMessagesLocator);
//
//            // The code then calls bodyLocator.page() for the iframe's Page
//            when(mockBodyLocator.page()).thenReturn(mockIframePage);
//
//            // Then waitForInitialMessages(...) calls chatMessagesLocator.first().waitFor(...)
//            when(mockChatMessagesLocator.first()).thenReturn(mockFirstLocator);
//            doNothing().when(mockFirstLocator).waitFor(any(Locator.WaitForOptions.class));
//
//            // Also, the code calls page.waitForTimeout(POLL_INTERVAL_MS) in a loop. We'll just ignore that:
//            doNothing().when(mockPage).waitForTimeout(anyLong());
//
//            // The code finally calls page.close() in the "finally" block
//            doNothing().when(mockPage).close();
//
//            // 4) Now run the consumer with our mock Browser
//            when(mockBrowser.newPage()).thenReturn(mockPage);
//            browserConsumer.accept(mockBrowser);
//
//            return null;
//        }).when(mockPool).withPage(any());
//
//        // 5) Execute the method under test
//        CompletableFuture<Void> future = scraperService.scrapeChannel("abcd1234");
//
//        // Because we run a while-loop until the future is done, we forcibly stop
//        scraperService.stopScraper("abcd1234");
//
//        // Check the future
//        assertTrue(future.isDone());
//        assertFalse(future.isCompletedExceptionally());
//
//        // Confirm the scraper state is COMPLETED
//        ScraperState state = scraperService.getScraperStates().get("abcd1234");
//        assertNotNull(state);
//        assertEquals(ScraperState.Status.COMPLETED, state.getStatus());
//    }
//
//
//    /**
//     * Test a scenario where "withBrowser(...)" throws an exception somewhere inside the scraping logic.
//     */
//    @Test
//    void testScrapeChannel_playwrightException() {
//        // We'll simulate a PlaywrightException so we can test that path
//        PlaywrightException mockPwe = new PlaywrightException("TargetClosedError: something happened");
//
//        doAnswer(invocation -> {
//            Consumer<Browser> consumer = invocation.getArgument(0);
//            // We'll just throw the exception from inside consumer
//            throw mockPwe;
//        }).when(mockPool).withPage(any());
//
//        CompletableFuture<Void> future = scraperService.scrapeChannel("failVid");
//        // Because of direct executor, this runs inline and should hit the catch block
//        assertTrue(future.isDone());
//        assertTrue(future.isCompletedExceptionally());
//
//        // The code sets status to FAILED
//        ScraperState st = scraperService.getScraperStates().get("failVid");
//        assertEquals(ScraperState.Status.FAILED, st.getStatus());
//        assertTrue(st.getErrorMessage().contains("TargetClosedError"));
//    }
//
//    /**
//     * Test the scenario where an unhandled exception (not a PlaywrightException) is thrown
//     * in the scraping logic. This covers the general catch block.
//     */
//    @Test
//    void testScrapeChannel_unhandledException() {
//        RuntimeException testEx = new RuntimeException("Something unexpected");
//
//        doAnswer(invocation -> {
//            Consumer<Browser> consumer = invocation.getArgument(0);
//            throw testEx;
//        }).when(mockPool).withPage(any());
//
//        CompletableFuture<Void> future = scraperService.scrapeChannel("oops");
//        assertTrue(future.isDone());
//        assertTrue(future.isCompletedExceptionally());
//
//        ScraperState st = scraperService.getScraperStates().get("oops");
//        assertEquals(ScraperState.Status.FAILED, st.getStatus());
//        assertTrue(st.getErrorMessage().contains("Something unexpected"));
//    }
//
//    @Test
//    void testStopScraper_noActiveFuture() {
//        String result = scraperService.stopScraper("doesNotExist");
//        assertEquals("No active scraper found for video ID: doesNotExist", result);
//    }
//
//    @Test
//    void testStopScraper_activeFuture() {
//        CompletableFuture<Void> f = new CompletableFuture<>();
//        scraperService.getActiveFutures().put("vidXYZ", f);
//        scraperService.getScraperStates().put("vidXYZ", new ScraperState("vidXYZ"));
//
//        String result = scraperService.stopScraper("vidXYZ");
//        assertEquals("Stopping scraper for video ID: vidXYZ", result);
//        assertTrue(f.isDone());
//
//        ScraperState st = scraperService.getScraperStates().get("vidXYZ");
//        assertEquals(ScraperState.Status.COMPLETED, st.getStatus());
//        assertEquals("Stopped by user.", st.getErrorMessage());
//    }
//
//    /**
//     * testProcessNewMessages covers the "no new messages" path and the normal iteration path.
//     */
//    @Test
//    void testProcessNewMessages() {
//        // We want to test processNewMessages(...) directly for coverage
//        // We'll mock a Locator that has some message count
//        Locator mockLocator = mock(Locator.class);
//        when(mockLocator.count()).thenReturn(5);
//
//        scraperService.processNewMessages("vid", "vTitle", "cName", mockLocator, 5);
//        // If current count <= processedCount, it returns early
//        verify(mockLocator, never()).nth(anyInt());
//
//        // If we have fewer "processedMessageCount" than actual, it processes them
//        when(mockLocator.count()).thenReturn(6);
//        Locator mockLocatorNth = mock(Locator.class);
//        when(mockLocator.nth(anyInt())).thenReturn(mockLocatorNth); // always the same
//        scraperService.processNewMessages("vid", "vTitle", "cName", mockLocator, 3);
//
//        // We expect it to call .nth(3), .nth(4), .nth(5)
//        verify(mockLocator, times(3)).nth(anyInt());
//    }
//
//    @Test
//    void testProcessSingleMessage() {
//        // We'll mock the "extractUsername" and "extractMessageText" indirectly by mocking the locator
//        // But those are actual methods in the service. We'll do partial coverage by letting the real code call them,
//        // or we can do a separate direct test of those methods. We'll do direct tests below, so here let's just test
//        // that we call broadcast, profanityLog, etc.
//
//        Locator mockMessageElement = mock(Locator.class, RETURNS_DEEP_STUBS);
//        // Let "extractUsername" and "extractMessageText" return some strings
//        // We'll actually call the real method, so let's do partial stubbing for the "innerText"/"evaluate" calls.
//        when(mockMessageElement.locator("xpath=.//*[@id='author-name']").innerText())
//                .thenReturn("TestUser");
//        when(mockMessageElement.evaluate(anyString())).thenReturn("Hello World!");
//
//        scraperService.processSingleMessage("video123", "VideoTitle", "ChannelXYZ", mockMessageElement);
//        // Confirm the chat message is broadcast
//        verify(mockBroadcastService).broadcast(eq("video123"), any(ChatMessage.class));
//        // Confirm the profanity check
//        verify(mockProfanityService).logIfProfane("TestUser", "Hello World!");
//        // Confirm the keywordRankingService usage
//        verify(mockKeywordRankingService).updateKeywordRanking("video123", "Hello World!");
//    }
//
//    @Test
//    void testProcessSingleMessage_exceptionCaught() {
//        Locator mockMessageElement = mock(Locator.class);
//        // Force an exception in extraction
//        when(mockMessageElement.locator(anyString())).thenThrow(new RuntimeException("Oops"));
//
//        // Should log the error and not rethrow
//        assertDoesNotThrow(() ->
//                scraperService.processSingleMessage("vId", "vTitle", "cName", mockMessageElement));
//        // No broadcast calls
//        verifyNoInteractions(mockBroadcastService, mockProfanityService, mockKeywordRankingService);
//    }
//
//    @Test
//    void testBuildStableKey() {
//        String key1 = scraperService.buildStableKey("bob", "Hi");
//        String key2 = scraperService.buildStableKey("bob", "Hi");
//        // Because it's the same minute, they should be the same.
//        assertEquals(key1, key2);
//    }
//
//    @Test
//    void testHandlePlaywrightException_targetClosed() {
//        PlaywrightException ex = new PlaywrightException("TargetClosedError: Something");
//        // We'll just call the method directly
//        // For coverage we can watch the logs, or just ensure no exception is thrown
//        // We'll do a partial check that the code hits the 'warn' path
//        scraperService.handlePlaywrightException("vid999", ex);
//        // The method logs a warning but does not rethrow
//    }
//
//    @Test
//    void testHandlePlaywrightException_otherError() {
//        PlaywrightException ex = new PlaywrightException("SomeOtherError: unknown");
//        scraperService.handlePlaywrightException("vid888", ex);
//        // logs an error but does not rethrow
//    }
//
//    @Test
//    void testParsePlaywrightError() {
//        // We'll call parsePlaywrightError(...) directly for coverage
//        PlaywrightException noMsg = new PlaywrightException(null);
//        assertEquals("Unknown Playwright error (no message).", callParseError(noMsg));
//
//        PlaywrightException timeoutErr = new PlaywrightException("TimeoutError 10000ms exceeded");
//        assertTrue(callParseError(timeoutErr).contains("Chat iframe not found"));
//
//        PlaywrightException resolvedErr = new PlaywrightException("net::err_name_not_resolved");
//        assertTrue(callParseError(resolvedErr).contains("Network issue: could not resolve the host"));
//
//        PlaywrightException disconnectedErr = new PlaywrightException("net::err_internet_disconnected");
//        assertTrue(callParseError(disconnectedErr).contains("No internet connection"));
//
//        PlaywrightException otherErr = new PlaywrightException("some random error");
//        assertTrue(callParseError(otherErr).contains("Playwright error: some random error"));
//    }
//
//    // Helper to call private parsePlaywrightError(...)
//    // If parsePlaywrightError is truly private, you might need reflection or
//    // change it to package-private for test coverage.
//    private String callParseError(PlaywrightException e) {
//        // We'll just do it via reflection if it's private.
//        // Or if you made it package-private, you can call directly: scraperService.parsePlaywrightError(e)
//        try {
//            var method = YTChatScraperService.class.getDeclaredMethod("parsePlaywrightError", PlaywrightException.class);
//            method.setAccessible(true);
//            return (String) method.invoke(scraperService, e);
//        } catch (Exception ex) {
//            throw new RuntimeException(ex);
//        }
//    }
//
//    /**
//     * A small utility class to forcibly set private fields for testing
//     * (avoids having to create a setter just for tests).
//     */
//    private static class TestUtils {
//        static void setField(Object target, String fieldName, Object value) {
//            try {
//                var field = target.getClass().getDeclaredField(fieldName);
//                field.setAccessible(true);
//                field.set(target, value);
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//        }
//    }
//}
