package csw.youtube.chat.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
@EnableAsync
public class AsyncConfig {

    // VT on
    @Primary
    @Bean(name = "chatScraperExecutor")
    public Executor chatScraperExecutor() {
        ThreadFactory vtFactory = Thread.ofVirtual()
                .name("yt-scraper-", 0)
                .factory();
        return Executors.newThreadPerTaskExecutor(vtFactory);
    }


    @Bean(name = "chatScraperExecutor1")
    public Executor chatScraperExecutor1() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);     // initial threads
        executor.setMaxPoolSize(30);      // maximum threads
        executor.setQueueCapacity(500);   // waiting tasks
        executor.setAllowCoreThreadTimeOut(true); // let the pool shrink back down if those core threads stay idle for too long.
        executor.setThreadNamePrefix("yt-scraper-");
        executor.initialize();
        return executor;
    }
}