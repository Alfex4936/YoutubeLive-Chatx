package csw.youtube.chat.playwright;

import com.microsoft.playwright.*;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Paths;
import java.util.List;

@Slf4j
public class PlaywrightBrowserHolder implements AutoCloseable {
    private final Playwright playwright;
    private final Browser browser;
    private final Thread bindingThread;

    public PlaywrightBrowserHolder() {
        this.bindingThread = Thread.currentThread(); // Store thread affinity at creation
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setArgs(List.of(
                                "--no-startup-window", // remove if headful
                                "--remote-debugging-port=0",
                                "--disable-popup-blocking",
                                "--disable-crash-reporter",
                                // "--disable-component-extensions-with-background-pages",
                                "--disable-sync-preferences",
//                                "--memory-pressure-off",
                                // "--disable-software-rasterizer", // Forces software rendering off (lowers CPU usage) - ERROR
                                "--disable-background-timer-throttling", // Ensures background pages aren't deprioritized
                                "--disable-renderer-backgrounding", // Prevents rendering slowdown when pages aren't active

                                "--no-sandbox",
                                "--disable-extensions",
                                "--disable-gpu",
                                "--disable-dev-shm-usage",
                                "--disable-setuid-sandbox",
//                                 "--single-process",
//                                 "--no-zygote",
                                "--disable-accelerated-2d-canvas",
                                "--disable-web-security",
                                // "--disable-background-networking",
                                "--disable-default-apps",
                                "--disable-sync",
                                "--disable-translate",
                                // "--hide-scrollbars",
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
        log.info("Created new PlaywrightBrowserHolder instance");
    }

    private void checkThreadSafety() {
        if (Thread.currentThread() != bindingThread) {
            throw new IllegalStateException(
                    "Playwright objects must be accessed from their creator threads! " +
                            "Created on " + bindingThread.getName() + ", but called on " + Thread.currentThread().getName()
            );
        }
    }

    public BrowserContext createContext(Browser.NewContextOptions options) {
        checkThreadSafety();
        return browser.newContext(options);
    }

    public Page createPage(Browser.NewContextOptions contextOptions) {
        checkThreadSafety();
        BrowserContext context = createContext(contextOptions);
        return context.newPage();
    }

    public boolean isHealthy() {
        checkThreadSafety();
        return browser.isConnected();
    }

    @Override
    public void close() {
        checkThreadSafety();
        try {
            browser.close();
            playwright.close();
            log.info("Successfully closed PlaywrightBrowserHolder on thread: {}", bindingThread.getName());
        } catch (Exception e) {
            log.error("Error closing PlaywrightBrowserHolder", e);
        }
    }
}