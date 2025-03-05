package csw.youtube.chat.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Slf4j
@Component
public class PlaywrightBrowserManager {

    private static final ThreadLocal<PlaywrightBrowserHolder> holderThreadLocal = new ThreadLocal<>();

    /**
     * Lazily create (or retrieve existing) BrowserHolder pinned to this thread.
     * In practice, with virtual threads, each request will get its own thread
     * and thus its own holder, which won't be reused.
     */
    private PlaywrightBrowserHolder getOrCreateHolder() {
        PlaywrightBrowserHolder holder = holderThreadLocal.get();
        if (holder == null) {
            holder = new PlaywrightBrowserHolder();
            holderThreadLocal.set(holder);
            log.info("Created new BrowserHolder for thread: {}", Thread.currentThread().getName());
        }
        return holder;
    }

    /**
     * Provide a Page to the given consumer, pinned to the thread's Holder.
     */
    public void withPage(Consumer<Page> pageConsumer) {
        PlaywrightBrowserHolder holder = getOrCreateHolder();

        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setViewportSize(1280, 720)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/133.0.0 Safari/537.36")
                .setJavaScriptEnabled(true);

        // Create a fresh context/page each time, but same thread & same holder
        try (BrowserContext ctx = holder.createContext(contextOptions);
             Page page = ctx.newPage()) {
            pageConsumer.accept(page);
        } catch (Exception e) {
            log.error("Error using PlaywrightBrowserHolder on thread {}", Thread.currentThread().getName(), e);
            throw new RuntimeException("Failed browser operation", e);
        }
    }

    /**
     * Optionally close the BrowserHolder for this current thread.
     * With virtual threads, once the thread ends, there's no re-use anyway;
     * so usually just let it die. But if I prefer, I can call this
     * at the end of logic to ensure a clean close.
     */
    public void closeHolderForCurrentThread() {
        PlaywrightBrowserHolder holder = holderThreadLocal.get();
        if (holder != null) {
            holder.close();
            holderThreadLocal.remove();
            log.info("Closed BrowserHolder for thread: {}", Thread.currentThread().getName());
        }
    }
}
