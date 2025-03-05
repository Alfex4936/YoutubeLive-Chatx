package csw.youtube.chat.playwright;

import com.microsoft.playwright.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class PlaywrightResources implements AutoCloseable {
    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;
    @Getter
    private final Page page;

    public PlaywrightResources(Playwright playwright) {
        this.playwright = playwright;
        this.browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setArgs(List.of(
                                // "--no-startup-window", // remove if headful
                                "--remote-debugging-port=0",
                                "--disable-popup-blocking",
                                "--disable-crash-reporter",
                                "--disable-component-extensions-with-background-pages",
                                "--disable-sync-preferences",
                                "--memory-pressure-off",
                                "--disable-software-rasterizer", // Forces software rendering off (lowers CPU usage) - ERROR
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
                                "--disable-default-apps",
                                "--disable-sync",
                                "--disable-translate",
                                 // "--hide-scrollbars",
                                 "--metrics-recording-only",
                                "--mute-audio",
                                "--no-first-run",
                                "--disable-backgrounding-occluded-windows"
                                // "--disable-background-networking"
                                // "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
                        ))

        );
        this.context = browser.newContext(
                new Browser.NewContextOptions()
                        .setViewportSize(1200, 600)
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/133.0.0 Safari/537.36")
        );
        this.page = context.newPage();
    }

    @Override
    public void close() {
        try {
            if (context != null) context.close();
            if (browser != null) browser.close();
            if (playwright != null) playwright.close();
            log.info("Playwright resources closed.");
        } catch (Exception e) {
            log.error("Error closing Playwright resources", e);
        }
    }
}