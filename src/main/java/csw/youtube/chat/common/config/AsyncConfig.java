package csw.youtube.chat.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    // VT on
    @Primary
    @Bean(name = "chatScraperExecutor")
    public Executor chatScraperExecutor() {
        ThreadFactory vtFactory = Thread.ofVirtual()
                .name("yt-scraper-", 0)
                .uncaughtExceptionHandler((thread, throwable) -> log.error("Uncaught exception in virtual thread: {}", thread.getName(), throwable))
                // .inheritInheritableThreadLocals()
                .factory();
        // One new virtual thread per task
        return Executors.newThreadPerTaskExecutor(vtFactory);
    }

//    @Bean(name = "scraperExecutor")
//    public Executor scraperExecutor() {
//        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
//        executor.setCorePoolSize(30); // 30 threads, so 30 browsers
//        executor.setMaxPoolSize(30); // Cap at 30 to limit resource usage
//        executor.setQueueCapacity(100); // Queue up to 100 tasks when all threads are busy
//        executor.setThreadNamePrefix("scraper-");
//        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // Run in caller’s thread if queue is full
//        executor.initialize();
//        return executor;
//    }
}