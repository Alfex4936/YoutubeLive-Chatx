package csw.youtube.chat.playwright.pool;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import csw.youtube.chat.playwright.PlaywrightBrowserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class PlaywrightPoolManager {
    private final Map<Thread, PlaywrightBrowserHolder> threadBrowserMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService healthCheckExecutor = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        // Schedule browser health checks every 5 minutes
        // healthCheckExecutor.scheduleAtFixedRate(this::checkBrowserHealth, 5, 5, TimeUnit.MINUTES);
    }

    public PlaywrightBrowserHolder getBrowser() {
        Thread currentThread = Thread.currentThread();
        return threadBrowserMap.computeIfAbsent(currentThread, t -> new PlaywrightBrowserHolder());
    }

    public <R> R withPage(Function<Page, R> pageFunction) {
        PlaywrightBrowserHolder holder = getBrowser();
        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setViewportSize(1280, 720)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
                .setJavaScriptEnabled(true);
        try (BrowserContext ctx = holder.createContext(contextOptions);
             Page page = ctx.newPage()) {
            return pageFunction.apply(page);
        } catch (Exception e) {
            throw new RuntimeException("Failed browser operation on thread " + Thread.currentThread().getName(), e);
        }
    }

    public void closeBrowser() {
        Thread currentThread = Thread.currentThread();
        PlaywrightBrowserHolder holder = threadBrowserMap.remove(currentThread);
        if (holder != null) {
            holder.close();
        }
    }

    private void checkBrowserHealth() {
        threadBrowserMap.forEach((thread, holder) -> {
            if (!holder.isHealthy()) {
                System.out.println("Browser for thread " + thread.getName() + " is unhealthy. Restarting...");
                holder.close();
                threadBrowserMap.put(thread, new PlaywrightBrowserHolder());
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        healthCheckExecutor.shutdown();
        threadBrowserMap.values().forEach(PlaywrightBrowserHolder::close);
    }
}