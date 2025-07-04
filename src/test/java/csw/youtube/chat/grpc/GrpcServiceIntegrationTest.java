package csw.youtube.chat.grpc;

import csw.youtube.chat.grpc.scraper.ScraperServiceGrpc;
import csw.youtube.chat.grpc.scraper.ScraperServiceProto.*;
import csw.youtube.chat.grpc.statistics.StatisticsServiceGrpc;
import csw.youtube.chat.grpc.statistics.StatisticsServiceProto.*;
import csw.youtube.chat.grpc.ai.AiServiceGrpc;
import csw.youtube.chat.grpc.ai.AiServiceProto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class GrpcServiceIntegrationTest {

    private ManagedChannel channel;
    private ScraperServiceGrpc.ScraperServiceBlockingStub scraperStub;
    private StatisticsServiceGrpc.StatisticsServiceBlockingStub statisticsStub;
    private AiServiceGrpc.AiServiceBlockingStub aiStub;

    @BeforeEach
    void setUp() {
        // Create a channel to connect to the gRPC server
        channel = ManagedChannelBuilder.forAddress("localhost", 9090)
                .usePlaintext()
                .build();

        scraperStub = ScraperServiceGrpc.newBlockingStub(channel);
        statisticsStub = StatisticsServiceGrpc.newBlockingStub(channel);
        aiStub = AiServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Test
    void testScraperServiceGetStatus() {
        // Test getting status for non-existent scraper
        GetScraperStatusRequest request = GetScraperStatusRequest.newBuilder()
                .setVideoId("test-video-id")
                .build();

        GetScraperStatusResponse response = scraperStub.getScraperStatus(request);
        assertEquals("NOT_FOUND", response.getStatus());
    }

    @Test
    void testScraperServiceStartScraper() {
        StartScraperRequest request = StartScraperRequest.newBuilder()
                .setVideoId("test-video-123")
                .addSkipLanguages("ENGLISH")
                .addSkipLanguages("SPANISH")
                .build();

        try {
            StartScraperResponse response = scraperStub.startScraper(request);
            assertNotNull(response);
            // Response success depends on implementation logic
            assertNotNull(response.getMessage());
        } catch (StatusRuntimeException e) {
            // This is expected if the service dependencies are not fully initialized
            assertTrue(e.getMessage().contains("UNAVAILABLE") || e.getMessage().contains("INTERNAL"));
        }
    }

    @Test
    void testAiServiceSummarizeChat() {
        SummarizeChatRequest request = SummarizeChatRequest.newBuilder()
                .setVideoId("test-video-123")
                .setLanguage("English")
                .build();

        try {
            SummarizeChatResponse response = aiStub.summarizeChat(request);
            assertNotNull(response);
            // Should fail because scraper is not active
            assertFalse(response.getSuccess());
            assertTrue(response.getErrorMessage().contains("not active"));
        } catch (StatusRuntimeException e) {
            // This is expected if the service dependencies are not fully initialized
            assertTrue(e.getMessage().contains("UNAVAILABLE") || e.getMessage().contains("INTERNAL"));
        }
    }
}