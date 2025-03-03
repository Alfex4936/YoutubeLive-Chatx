package csw.youtube.chat.playwright;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

@SpringBootTest
class PlaywrightManagerStressTest {
    @Autowired
    private PlaywrightBrowserManager browserManager;

    @Test
    void stressTestBrowserIsolation() throws InterruptedException {
        int concurrentTests = 5;
        boolean systemOverloaded = false;

        // We keep increasing concurrency until we detect "overload."
        while (!systemOverloaded) {
            System.out.println("\nRunning " + concurrentTests + " stress-test tasks concurrently.");

            ExecutorService executor = Executors.newFixedThreadPool(concurrentTests);
            CountDownLatch latch = new CountDownLatch(concurrentTests);

            for (int i = 0; i < concurrentTests; i++) {
                executor.submit(() -> {
                    try {
                        // This calls manager.withPage(...) which ensures
                        // each thread uses its own BrowserHolder.
                        browserManager.withPage(page -> {
                            System.out.println("▶ Thread " + Thread.currentThread().getName() + ": Navigating...");
                            page.navigate("https://www.youtube.com/watch?v=jfKfPfyJRdk",
                                    new Page.NavigateOptions().setTimeout(20000)); // 20s timeout
                            page.waitForLoadState(LoadState.LOAD,
                                    new Page.WaitForLoadStateOptions().setTimeout(15000));
                            // Potentially do more stuff here...
                        });
                    } catch (Exception e) {
                        System.err.println("🚨 Playwright Error in thread " +
                                Thread.currentThread().getName() + ": " + e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Wait for all tasks to finish (or time out).
            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Check system CPU & memory usage:
            long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            double usedMemoryMB = usedMemory / (1024.0 * 1024.0);
            double maxMemoryMB = Runtime.getRuntime().maxMemory() / (1024.0 * 1024.0);

            double systemCpuLoad = getSystemCpuLoad() * 100.0;  // convert to %
            System.out.printf("🖥  Used Memory: %.2f MB / %.2f MB  |  CPU Load: %.2f%%%n",
                    usedMemoryMB, maxMemoryMB, systemCpuLoad);

            // Decide if "overloaded" (arbitrary thresholds).
            if (systemCpuLoad > 85.0 || usedMemoryMB > (maxMemoryMB * 0.8)) {
                systemOverloaded = true;
            } else {
                concurrentTests += 5;  // Increase concurrency step
            }
        }

        System.out.println("🚨 System reached overload limit at " + concurrentTests + " concurrent tasks.");
    }

    private double getSystemCpuLoad() {
        OperatingSystemMXBean osBean =
                (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        return osBean.getCpuLoad(); // returns a value from 0.0 to 1.0
    }
}
