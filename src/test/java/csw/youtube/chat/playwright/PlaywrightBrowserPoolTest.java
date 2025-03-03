package csw.youtube.chat.playwright;

import com.microsoft.playwright.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Example test class using JUnit 5 and Mockito to achieve high coverage on PlaywrightBrowserPool.
 */
@ExtendWith(MockitoExtension.class)
class PlaywrightBrowserPoolTest {

    @InjectMocks
    private PlaywrightBrowserPool pool; // Our class under test

    @Mock
    private Playwright mockPlaywright;

    @Mock
    private Browser mockBrowser;

    @Mock
    private GenericObjectPool<Browser> mockGenericPool;

    @Captor
    private ArgumentCaptor<Browser> browserCaptor;

    @Spy
    private Logger logSpy; // If you want to capture log lines, or just skip logging in tests

    @BeforeEach
    void setUp() {
        /*
         * We inject our mocks into "pool" so that calls like "playwright = Playwright.create()"
         * can be replaced with a mock. Then we can fully test the code without launching a real browser.
         *
         * In real usage, you'd restructure your code slightly so "playwright = Playwright.create()"
         * is more easily mockable (e.g., via a factory or direct injection).
         */
        // If not using a different approach, you can still forcibly set "pool.playwright = mockPlaywright" in test
        pool = new PlaywrightBrowserPool();
        // We'll need to manually set the values of our @Value fields, since in tests they won't be auto-wired
        pool.setMaxTotal(2);
        pool.setMaxIdle(1);
        pool.setMinIdle(1);
        pool.setMaxWaitMillis(30000);
        pool.setTimeBetweenEvictionRunsMillis(60000);
        pool.setMinEvictableIdleTimeMillis(60000);

        // We can also replace the real pool with a mock if we want to test only some logic
        // but here we'll let the real initialization run and then intercept the creation logic
    }

    @Test
    void testInitialize_successfulPreInit() {
        // We want to ensure the code in initialize() is tested, including pre-initializing objects
        // We'll stub "Playwright.create()" and "addObject()" calls to simulate success.
        Playwright realPlaywright = mock(Playwright.class, withSettings().lenient());
        // However, because your code calls "Playwright.create()" directly, we'd have to mock it statically or
        // do partial re-architecture. For the sake of coverage demonstration, let's do the following:

        pool.initialize(); // This calls "Playwright.create()" by default

        // Because we haven't truly replaced "playwright", we won't get real coverage on the creation path
        // You can forcibly set pool.playwright after the call so we can at least test some usage paths
        assertNotNull(pool.getPlaywright(), "Playwright should not be null after initialization.");
        assertDoesNotThrow(() -> pool.withBrowser(b -> {}));
    }

    @Test
    void testInitialize_exceptionInPreInit() throws Exception {
        // Suppose we want to ensure coverage of the catch block when pre-initializing browsers fails.
        // We can do that by forcing an exception from "browserPool.addObject()".

        // 1) Spy on the pool so we can mock the GenericObjectPool
        PlaywrightBrowserPool spyPool = Mockito.spy(pool);

        // 2) Provide a mock for the internal pool
        GenericObjectPool<Browser> mockPool = mock(GenericObjectPool.class);
        doThrow(new RuntimeException("addObject failed!")).when(mockPool).addObject();

        // 3) Force spy to return the mock pool in "initialize()"
        doReturn(mockGenericPool).when(spyPool).createGenericPool(any());
        // But your code doesn't use a separate method to create the pool. So you might refactor
        // or do partial mocking of "new GenericObjectPool<>(...)".
        // For illustration, let's pretend we do a helper that calls "new GenericObjectPool(...)"

        spyPool.initialize(); // This triggers the block that tries to addObject()
        // The exception is caught and logged, so no test exception is thrown.
        // That means we've covered the catch block.

        // Verify it tried to pre-init:
        // (Of course, you might need to adapt your code to allow hooking in easily.)
        verify(mockPool, atLeastOnce()).addObject();
    }

    @Test
    void testWithBrowser_success() throws Exception {
        // We want 100% coverage in the withBrowser method, including the happy path
        // We'll do the following:
        //  - Stub the internal pool's borrowObject() to return a mockBrowser
        //  - Use a simple Consumer that sets a boolean

        // We'll force the real "browserPool" field to be a mock:
        pool.setBrowserPool(mockGenericPool);

        when(mockGenericPool.borrowObject()).thenReturn(mockBrowser);

        AtomicBoolean invoked = new AtomicBoolean(false);

        pool.withBrowser(browser -> {
            assertSame(mockBrowser, browser);
            invoked.set(true);
        });

        assertTrue(invoked.get(), "Consumer should have been invoked.");
        // Check that it returned the browser
        verify(mockGenericPool).returnObject(mockBrowser);
    }

    @Test
    void testWithBrowser_exceptionInConsumer() throws Exception {
        // We want to test the code path that catches an exception from 'consumer.accept(browser)'
        pool.setBrowserPool(mockGenericPool);
        when(mockGenericPool.borrowObject()).thenReturn(mockBrowser);

        RuntimeException testException = new RuntimeException("Test exception in consumer");
        assertThrows(RuntimeException.class, () -> {
            pool.withBrowser(browser -> {
                throw testException;
            });
        });

        // The code should have called invalidateObject(...) on the failing browser.
        verify(mockGenericPool).invalidateObject(mockBrowser);
        verify(mockGenericPool, never()).returnObject(any()); // Because we invalidated it instead.
    }

    @Test
    void testWithBrowserContext_success() throws Exception {
        // We'll do a success path for withBrowserContext:
        pool.setBrowserPool(mockGenericPool);
        when(mockGenericPool.borrowObject()).thenReturn(mockBrowser);

        // We mock the newContext() call on Browser
        BrowserContext mockContext = mock(BrowserContext.class);
        when(mockBrowser.newContext(any())).thenReturn(mockContext);

        // Inside the consumer, let's set a boolean
        AtomicBoolean invoked = new AtomicBoolean(false);
        // We can verify options if we want
        pool.withBrowserContext(Assertions::assertNotNull, ctx -> {
            assertSame(mockContext, ctx);
            invoked.set(true);
        });

        // The context was used, and closed automatically
        assertTrue(invoked.get());
        verify(mockContext).close();
    }

    @Test
    void testWithPage_success() throws Exception {
        // We'll do a success path for withPage:
        pool.setBrowserPool(mockGenericPool);
        when(mockGenericPool.borrowObject()).thenReturn(mockBrowser);

        BrowserContext mockContext = mock(BrowserContext.class);
        when(mockBrowser.newContext(any())).thenReturn(mockContext);

        Page mockPage = mock(Page.class);
        when(mockContext.newPage()).thenReturn(mockPage);

        AtomicBoolean invoked = new AtomicBoolean(false);
        pool.withPage(opts -> {
            // context configurator
        }, page -> {
            assertSame(mockPage, page);
            invoked.set(true);
        });

        assertTrue(invoked.get());
        verify(mockPage).close();
        verify(mockContext).close();
    }

    @Test
    void testWithPage_noConfigurator() throws Exception {
        // Shortcut method withPage(Consumer<Page> consumer)
        pool.setBrowserPool(mockGenericPool);
        when(mockGenericPool.borrowObject()).thenReturn(mockBrowser);

        BrowserContext mockContext = mock(BrowserContext.class);
        when(mockBrowser.newContext(any())).thenReturn(mockContext);

        Page mockPage = mock(Page.class);
        when(mockContext.newPage()).thenReturn(mockPage);

        pool.withPage(page -> {
            assertSame(mockPage, page);
        });

        verify(mockPage).close();
        verify(mockContext).close();
    }

    @Test
    void testCleanup() throws Exception {
        // We want to test coverage of the cleanup method.
        // Let us mock the calls so we can verify them
        pool.setBrowserPool(mockGenericPool);

        // Because your code calls "playwright.close()"
        pool.setPlaywright(mockPlaywright);

        // Now call cleanup:
        pool.cleanup();

        verify(mockGenericPool).close();
        verify(mockPlaywright).close();
    }

    @Test
    void testCleanup_poolCloseException() throws Exception {
        // Coverage of the catch block for "browserPool.close()"
        pool.setBrowserPool(mockGenericPool);
        pool.setPlaywright(mockPlaywright);

        doThrow(new RuntimeException("Pool close failed")).when(mockGenericPool).close();

        // Should log error but not throw
        assertDoesNotThrow(() -> pool.cleanup());

        verify(mockGenericPool).close();
        verify(mockPlaywright).close();
    }

    @Test
    void testCleanup_playwrightCloseException() throws Exception {
        // Coverage of the catch block for "playwright.close()"
        pool.setBrowserPool(mockGenericPool);
        pool.setPlaywright(mockPlaywright);

        doThrow(new RuntimeException("Playwright close failed")).when(mockPlaywright).close();

        // Should log error but not throw
        assertDoesNotThrow(() -> pool.cleanup());

        verify(mockGenericPool).close();
        verify(mockPlaywright).close();
    }

    @Test
    void testBrowserFactory_create() {
        // We want coverage of the factory's create() method.
        // We'll do a partial test: We'll create the factory with a mock playwright
        PlaywrightBrowserPool.PlaywrightBrowserFactory factory =
                new PlaywrightBrowserPool.PlaywrightBrowserFactory(mockPlaywright);

        // Mock returning a mocked BrowserType
        BrowserType mockBrowserType = mock(BrowserType.class);
        when(mockPlaywright.chromium()).thenReturn(mockBrowserType);

        // Also stub the "launch" method
        when(mockBrowserType.launch(any())).thenReturn(mockBrowser);

        Browser created = factory.create();
        assertNotNull(created);
        verify(mockBrowserType).launch(any());
    }

    @Test
    void testBrowserFactory_wrap() {
        PlaywrightBrowserPool.PlaywrightBrowserFactory factory =
                new PlaywrightBrowserPool.PlaywrightBrowserFactory(mockPlaywright);

        PooledObject<Browser> pooled = factory.wrap(mockBrowser);
        assertNotNull(pooled);
        assertSame(mockBrowser, pooled.getObject());
    }

    @Test
    void testBrowserFactory_validateObject_connected() {
        // If isConnected() returns true, should be validated
        when(mockBrowser.isConnected()).thenReturn(true);
        PlaywrightBrowserPool.PlaywrightBrowserFactory factory =
                new PlaywrightBrowserPool.PlaywrightBrowserFactory(mockPlaywright);

        PooledObject<Browser> pObject = factory.wrap(mockBrowser);
        assertTrue(factory.validateObject(pObject));
    }

    @Test
    void testBrowserFactory_validateObject_notConnected() {
        // If isConnected() throws or returns false, validation fails
        when(mockBrowser.isConnected()).thenThrow(new RuntimeException("Not connected"));
        PlaywrightBrowserPool.PlaywrightBrowserFactory factory =
                new PlaywrightBrowserPool.PlaywrightBrowserFactory(mockPlaywright);

        PooledObject<Browser> pObject = factory.wrap(mockBrowser);
        assertFalse(factory.validateObject(pObject));
    }

    @Test
    void testBrowserFactory_destroyObject() {
        PlaywrightBrowserPool.PlaywrightBrowserFactory factory =
                new PlaywrightBrowserPool.PlaywrightBrowserFactory(mockPlaywright);

        PooledObject<Browser> pooled = factory.wrap(mockBrowser);

        factory.destroyObject(pooled);
        verify(mockBrowser).close();
    }

    /*
     * Depending on your testing framework or coverage tool, you might also
     * need to ensure all @Value setters are covered. Since they're simple
     * setters in your code, it might not be strictly necessary, but you can
     * add small tests like:
     */
    @Test
    void testSetters() {
        pool.setMaxTotal(5);
        pool.setMaxIdle(3);
        pool.setMinIdle(2);
        pool.setMaxWaitMillis(20000);
        pool.setTimeBetweenEvictionRunsMillis(10000);
        pool.setMinEvictableIdleTimeMillis(9999);

        assertEquals(5, pool.getMaxTotal());
        assertEquals(3, pool.getMaxIdle());
        assertEquals(2, pool.getMinIdle());
        assertEquals(20000, pool.getMaxWaitMillis());
        assertEquals(10000, pool.getTimeBetweenEvictionRunsMillis());
        assertEquals(9999, pool.getMinEvictableIdleTimeMillis());
    }
}
