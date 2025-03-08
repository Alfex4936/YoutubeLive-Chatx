package csw.youtube.chat.playwright.pool;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class PlaywrightWorkerPool {
    private final BlockingQueue<PlaywrightWorker> pool;

    public PlaywrightWorkerPool(@Value("${scraper.poolSize:2}") int poolSize) {
        pool = new LinkedBlockingQueue<>();
        for (int i = 0; i < poolSize; i++) {
            pool.offer(new PlaywrightWorker());
        }
    }

    public PlaywrightWorker acquireWorker() throws InterruptedException {
        return pool.take();
    }

    public void releaseWorker(PlaywrightWorker worker) {
        try {
            worker.resetPage();
        } catch (Exception e) {
            // Log the error and decide whether to discard the worker
            e.printStackTrace();
        }
        pool.offer(worker);
    }

    @PreDestroy
    public void shutdown() {
        for (PlaywrightWorker worker : pool) {
            worker.shutdownWorker();
        }
    }
}