package csw.youtube.chat.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    // VT on
    @Bean(name = "chatScraperExecutor")
    public Executor chatScraperExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
//        executor.setCorePoolSize(10);     // initial threads
//        executor.setMaxPoolSize(20);      // maximum threads
//        executor.setQueueCapacity(500);   // waiting tasks
        executor.setThreadNamePrefix("yt-scraper-");
        executor.initialize();
        return executor;
    }
}