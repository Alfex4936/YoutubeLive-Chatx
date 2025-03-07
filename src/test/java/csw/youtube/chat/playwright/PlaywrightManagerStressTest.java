package csw.youtube.chat.playwright;


import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.management.ManagementFactory;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(classes = {PlaywrightBrowserManager.class})
public class PlaywrightManagerStressTest {

    @Autowired
    private PlaywrightBrowserManager browserManager;

    // Counter to track currently alive (active) pages.
    private final AtomicInteger livingPages = new AtomicInteger(0);

    @Test
    void persistentPagesStressTest() throws InterruptedException {
        int concurrentTests = 5;
        boolean systemOverloaded = false;
        // Using a cached thread pool so that new tasks can be submitted without bounds.
        ExecutorService executor = Executors.newCachedThreadPool();

        // Run the load test until system memory crosses 80% of max available memory.
        while (!systemOverloaded) {
            System.out.println("\nLaunching " + concurrentTests + " new tasks.");
            for (int i = 0; i < concurrentTests; i++) {
                executor.submit(() -> {
                    // Increase the counter for a new persistent page.
                    livingPages.incrementAndGet();
                    try {
                        browserManager.withPage(page -> {
                            // Navigate and wait for the page to load.
                            page.navigate("https://www.youtube.com/watch?v=jfKfPfyJRdk",
                                    new Page.NavigateOptions().setTimeout(20000));
                            page.waitForLoadState(LoadState.LOAD,
                                    new Page.WaitForLoadStateOptions().setTimeout(15000));

                            // Hold the page alive for 10 minutes.
                            System.out.println("Holding page open in thread: " + Thread.currentThread().getName());
                            try {
                                Thread.sleep(TimeUnit.MINUTES.toMillis(10));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
                    } catch (Exception e) {
                        System.err.println("Error in thread " + Thread.currentThread().getName() + ": " + e);
                    } finally {
                        // When the task ends (after sleep), decrement the counter.
                        livingPages.decrementAndGet();
                    }
                });
            }

            // Wait a short period to let tasks start and settle.
            Thread.sleep(30_000);

            // Gather system metrics.
            double systemCpuLoad = getSystemCpuLoad() * 100.0;
            double usedMemoryMB = getUsedMemoryMB();
            double maxMemoryMB = getMaxMemoryMB();

            // Log current load and system metrics.
            System.out.printf("Living Pages: %d | New Tasks Launched: %d | CPU=%.2f%% | Mem=%.2fMB/%.2fMB%n",
                    livingPages.get(), concurrentTests, systemCpuLoad, usedMemoryMB, maxMemoryMB);

            // Increase load if memory usage is below threshold.
            if (usedMemoryMB > (maxMemoryMB * 0.8)) {
                systemOverloaded = true;
            } else {
                concurrentTests += 5;  // Increase load by launching additional tasks.
            }
        }

        System.out.println("🚨 System reached overload limit. Shutting down executor.");
        executor.shutdownNow();
    }

    private double getSystemCpuLoad() {
        com.sun.management.OperatingSystemMXBean osBean =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        return osBean.getCpuLoad(); // returns value between 0.0 and 1.0
    }

    private double getUsedMemoryMB() {
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return usedMemory / (1024.0 * 1024.0);
    }

    private double getMaxMemoryMB() {
        return Runtime.getRuntime().maxMemory() / (1024.0 * 1024.0);
    }
}