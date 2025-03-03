package csw.youtube.chat.playwright;

import com.microsoft.playwright.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
public class PlaywrightBrowserPool {
    @Value("${playwright.pool.max-total:10}")
    @Setter
    @Getter
    private int maxTotal;

    @Value("${playwright.pool.max-idle:5}")
    @Setter
    @Getter
    private int maxIdle;

    @Value("${playwright.pool.min-idle:2}")
    @Setter
    @Getter
    private int minIdle;

    @Value("${playwright.pool.max-wait-millis:30000}")
    @Setter
    @Getter
    private long maxWaitMillis;

    @Value("${playwright.pool.time-between-eviction-runs-millis:300000}")
    @Setter
    @Getter
    private long timeBetweenEvictionRunsMillis;

    @Value("${playwright.pool.min-evictable-idle-time-millis:600000}")
    @Setter
    @Getter
    private long minEvictableIdleTimeMillis;

    @Getter
    @Setter
    private Playwright playwright;
    @Setter
    private GenericObjectPool<Browser> browserPool;

    @PostConstruct
    public void initialize() {
        log.info("Initializing Playwright browser pool with maxTotal={}, maxIdle={}, minIdle={}",
                maxTotal, maxIdle, minIdle);

        playwright = Playwright.create(); // Only one Playwright instance is needed

        final GenericObjectPoolConfig<Browser> config = getBrowserGenericObjectPoolConfig();

        // Create the pool with a custom Factory for Browser instances
        browserPool = createGenericPool(playwright);

        // Pre-initialize min idle browsers
        try {
            for (int i = 0; i < minIdle; i++) {
                browserPool.addObject();
            }
            log.info("Successfully pre-initialized {} browser instances", minIdle);
        } catch (Exception e) {
            log.error("Failed to pre-initialize browser instances", e);
        }
    }

    /**
     * Helper method that creates the GenericObjectPool using our config and the custom Factory.
     * This method can easily be mocked in your unit tests if needed.
     */
    public GenericObjectPool<Browser> createGenericPool(Playwright playwright) {
        final GenericObjectPoolConfig<Browser> config = getBrowserGenericObjectPoolConfig();
        return new GenericObjectPool<>(new PlaywrightBrowserFactory(playwright), config);
    }

    private GenericObjectPoolConfig<Browser> getBrowserGenericObjectPoolConfig() {
        GenericObjectPoolConfig<Browser> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(maxTotal);
        config.setMaxIdle(maxIdle);
        config.setMinIdle(minIdle);
        config.setMaxWait(Duration.ofMillis(maxWaitMillis));
        config.setTimeBetweenEvictionRuns(Duration.ofMillis(timeBetweenEvictionRunsMillis));
        config.setMinEvictableIdleDuration(Duration.ofMillis(minEvictableIdleTimeMillis));

        // Commons-pool will call validateObject(...) in these cases.
        config.setTestOnBorrow(true);
        config.setTestOnReturn(true);

        // Usually turned off unless you specifically want JMX
        config.setJmxEnabled(false);
        return config;
    }

    /**
     * Borrow a Browser from the pool, run the operation, and return it.
     *
     * @param consumer The operation to execute with an active Browser.
     */
    public void withBrowser(Consumer<Browser> consumer) {
        Browser browser = null;
        try {
            long startTime = System.currentTimeMillis();
            browser = browserPool.borrowObject();
            log.debug("Borrowed browser from pool in {}ms. Active: {}, Idle: {}",
                    System.currentTimeMillis() - startTime,
                    browserPool.getNumActive(),
                    browserPool.getNumIdle());

            consumer.accept(browser);
        } catch (Exception e) {
            log.error("Error while using browser from pool", e);
            if (browser != null) {
                // Invalidate the browser so the pool discards it and creates a fresh one later
                try {
                    browserPool.invalidateObject(browser);
                    browser = null;
                } catch (Exception invalidateEx) {
                    log.error("Failed to invalidate browser", invalidateEx);
                }
            }
            throw new RuntimeException("Failed to execute browser operation", e);
        } finally {
            if (browser != null) {
                browserPool.returnObject(browser);
                log.debug("Returned browser to pool. Active: {}, Idle: {}",
                        browserPool.getNumActive(),
                        browserPool.getNumIdle());
            }
        }
    }

    /**
     * Borrows a Browser and creates a new BrowserContext. The context can be configured.
     *
     * @param contextConfigurator Optional configurator for the browser context
     * @param consumer The operation to execute
     */
    public void withBrowserContext(Consumer<Browser.NewContextOptions> contextConfigurator,
                                   Consumer<BrowserContext> consumer) {
        withBrowser(browser -> {
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                    .setViewportSize(1280, 720)
                    .setDeviceScaleFactor(1)
                    .setJavaScriptEnabled(true)
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
                    .setLocale("en-US");

            if (contextConfigurator != null) {
                contextConfigurator.accept(contextOptions);
            }

            try (BrowserContext context = browser.newContext(contextOptions)) {
                consumer.accept(context);
            }
        });
    }

    /**
     * Borrows a Browser, creates a BrowserContext, then a Page. The page is closed after use.
     *
     * @param contextConfigurator Optional configurator for the browser context
     * @param consumer The operation to execute
     */
    public void withPage(Consumer<Browser.NewContextOptions> contextConfigurator,
                         Consumer<Page> consumer) {
        withBrowserContext(contextConfigurator, context -> {
            try (Page page = context.newPage()) {
                consumer.accept(page);
            }
        });
    }

    /**
     * Shortcut for a Page with default context settings.
     *
     * @param consumer The operation to execute
     */
    public void withPage(Consumer<Page> consumer) {
        withPage(null, consumer);
    }

    @PreDestroy
    public void cleanup() {
        log.info("Shutting down Playwright browser pool");
        try {
            browserPool.close();
            log.info("Browser pool closed successfully");
        } catch (Exception e) {
            log.error("Error closing browser pool", e);
        }

        try {
            playwright.close();
            log.info("Playwright instance closed successfully");
        } catch (Exception e) {
            log.error("Error closing Playwright instance", e);
        }
    }

    /**
     * Factory to create pooled browser objects
     */
    static class PlaywrightBrowserFactory extends BasePooledObjectFactory<Browser> {
        private final Playwright playwright;
        public PlaywrightBrowserFactory(Playwright playwright) {
            this.playwright = playwright;
        }

        @Override
        public Browser create() {
            log.debug("Creating new browser instance");
            long startTime = System.currentTimeMillis();

            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            .setArgs(List.of(
                                    "--no-sandbox",
                                    "--disable-extensions",
                                    "--disable-gpu",
                                    "--disable-dev-shm-usage",
                                    "--disable-setuid-sandbox",
                                    "--single-process",
                                    "--no-zygote",
                                    "--disable-accelerated-2d-canvas",
                                    "--disable-web-security",
                                    "--disable-background-networking",
                                    "--disable-default-apps",
                                    "--disable-sync",
                                    "--disable-translate",
                                    "--hide-scrollbars",
                                    "--metrics-recording-only",
                                    "--mute-audio",
                                    "--no-first-run",
                                    "--disable-backgrounding-occluded-windows",
                                    "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
                            ))
                            .setTimeout(30000)
                            .setIgnoreDefaultArgs(List.of("--enable-automation"))
                            .setSlowMo(0)
                            .setDownloadsPath(Paths.get("/tmp/downloads"))
            );

            log.debug("Created new browser instance in {}ms", System.currentTimeMillis() - startTime);
            return browser;
        }

        @Override
        public PooledObject<Browser> wrap(Browser browser) {
            return new DefaultPooledObject<>(browser);
        }

        @Override
        public boolean validateObject(PooledObject<Browser> p) {
            // isConnected() checks if the underlying browser process is alive & connected.
            // TODO could do a quick no-op page creation or a context creation test.
            Browser browser = p.getObject();
            try {
                return browser.isConnected();
            } catch (Exception ex) {
                log.warn("Browser validation failed", ex);
                return false;
            }
        }

        @Override
        public void destroyObject(PooledObject<Browser> p) {
            Browser browser = p.getObject();
            try {
                log.debug("Destroying browser instance");
                browser.close();
            } catch (Exception e) {
                log.error("Error closing browser", e);
            }
        }
    }
}
