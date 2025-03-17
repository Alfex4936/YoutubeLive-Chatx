package csw.youtube.chat.playwright.pool;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import csw.youtube.chat.live.js.YouTubeChatScriptProvider;

import java.util.List;
import java.util.concurrent.*;

public class PlaywrightWorker extends Thread {
    private final BlockingQueue<Callable<Object>> taskQueue = new LinkedBlockingQueue<>();
    private final CountDownLatch initialized = new CountDownLatch(1);
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private volatile boolean running = true;

    public PlaywrightWorker() {
        setDaemon(true);
        start(); // start the thread so that run() is executed
        try {
            initialized.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Worker initialization interrupted", e);
        }
    }

    @Override
    public void run() {
        // Initialize Playwright and browser on this dedicated thread.
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setArgs(List.of(
                        "--remote-debugging-port=0",
                        "--disable-popup-blocking",
                        "--disable-crash-reporter",
                        "--disable-sync-preferences",
                        "--disable-background-timer-throttling",
                        "--disable-renderer-backgrounding",
                        "--no-sandbox",
                        "--disable-extensions",
                        "--disable-gpu",
                        "--disable-dev-shm-usage",
                        "--disable-setuid-sandbox",
                        "--disable-accelerated-2d-canvas",
                        "--disable-web-security",
                        "--disable-default-apps",
                        "--disable-sync",
                        "--disable-translate",
                        "--metrics-recording-only",
                        "--mute-audio",
                        "--no-first-run",
                        "--disable-backgrounding-occluded-windows",
                        "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
                ))
                .setTimeout(30000)
                .setIgnoreDefaultArgs(List.of("--enable-automation"))
                .setSlowMo(0));
        context = browser.newContext();
        page = context.newPage();

        // Signal that initialization is complete.
        initialized.countDown();

        // Main loop: process tasks submitted to this worker.
        while (running) {
            try {
                Callable<Object> task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                if (task != null) {
                    try {
                        task.call();
                    } catch (Exception e) {
                        e.printStackTrace(); // log as needed
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // Submit a task and block until it’s done.
    public <T> T submit(Callable<T> task) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        taskQueue.offer(() -> {
            try {
                T value = task.call();
                result.complete(value);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
            return null;
        });
        return result.get(); // waits for task completion
    }

    // Convenience method for a Runnable.
    public void submit(Runnable task) throws Exception {
        submit(() -> {
            task.run();
            return null;
        });
    }

    // Access the page. Note: use it only inside a submit() call.
    public Page getPage() {
        return page;
    }

    // Reset the page state (e.g. navigate to "about:blank" and wait for load).
    public void resetPage() throws Exception {
        if (page == null) {
            return;
        }
        submit(() -> {
            page.evaluate(YouTubeChatScriptProvider.getDisconnectObserverScript());
            page.navigate("about:blank", new Page.NavigateOptions().setTimeout(10000));
            page.waitForLoadState(LoadState.LOAD, new Page.WaitForLoadStateOptions().setTimeout(10000));
            return null;
        });
    }

    // Shutdown this worker and release its resources.
    public void shutdownWorker() {
        running = false;
        try {
            submit(() -> {
                context.close();
                browser.close();
                playwright.close();
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.interrupt();
    }
}
