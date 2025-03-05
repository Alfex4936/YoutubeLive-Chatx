package csw.youtube.chat.playwright;

import csw.youtube.chat.live.dto.KeywordRankingPair;
import csw.youtube.chat.live.model.ScraperState;
import csw.youtube.chat.live.service.KeywordRankingService;
import csw.youtube.chat.live.service.YTChatScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// GET http://localhost:8080/actuator/scraper-stats
@Component
@Endpoint(id = "scraper-stats")
@RequiredArgsConstructor
public class ScraperStatsEndpoint {

    private final YTChatScraperService ytChatScraperService;
    private final KeywordRankingService keywordRankingService;

    @ReadOperation
    public Map<String, ScraperMetrics> getScraperStats() {
        Map<String, ScraperMetrics> statsMap = new HashMap<>();

        // For each scraper state, build a metrics object
        for (Map.Entry<String, ScraperState> entry : ytChatScraperService.getScraperStates().entrySet()) {
            String videoId = entry.getKey();
            ScraperState state = entry.getValue();

            // Retrieve top 5 keywords
            List<KeywordRankingPair> topKeywordsWithScores = keywordRankingService.getTopKeywordStrings(videoId, 5);

            ScraperMetrics metrics = new ScraperMetrics(
                    state.getVideoTitle(),
                    state.getChannelName(),
                    state.getVideoUrl(),
                    state.getStatus(),
                    state.getLastThroughput(),
                    state.getMaxThroughput(),
                    state.getAverageThroughput(),
                    state.getTotalMessages().get(),
                    topKeywordsWithScores,
                    state.getThreadName(),
                    state.getCreatedAt(),
                    state.getErrorMessage()
            );
            statsMap.put(videoId, metrics);
        }

        return statsMap;
    }

    /**
     * Represents the metrics we want to expose for each video ID.
     */
    public record ScraperMetrics(
            String videoTitle,
            String channelName,
            String videoUrl,
            ScraperState.Status status,
            int lastThroughput,
            int maxThroughput,
            double averageThroughput,
            long totalMessages,
            List<KeywordRankingPair> topKeywords,
            String threadName,
            Instant createdAt,
            String errorMessage
    ) {
    }
}
