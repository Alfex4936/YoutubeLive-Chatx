package csw.youtube.chat.grpc.config;

import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@Slf4j
@Configuration
public class GrpcServerConfig {

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("=== gRPC Server Configuration ===");
        log.info("gRPC Server is starting on port 9090");
        log.info("Available gRPC Services:");
        log.info("  - ScraperService (start/stop scrapers)");
        log.info("  - StatisticsService (metrics and data)");
        log.info("  - AiService (chat summarization)");
        log.info("  - UserService (user management)");
        log.info("gRPC Reflection is enabled for testing");
        log.info("REST endpoints remain available for backward compatibility");
        log.info("=================================");
    }
}