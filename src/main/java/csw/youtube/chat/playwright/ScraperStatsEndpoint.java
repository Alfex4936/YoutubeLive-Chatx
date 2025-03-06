package csw.youtube.chat.playwright;

import com.sun.management.OperatingSystemMXBean;
import csw.youtube.chat.live.dto.KeywordRankingPair;
import csw.youtube.chat.live.model.ScraperState;
import csw.youtube.chat.live.service.KeywordRankingService;
import csw.youtube.chat.live.service.YTChatScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.*;

// GET http://localhost:8080/actuator/scraper-stats
@Component
@Endpoint(id = "scraper-stats")
@RequiredArgsConstructor
public class ScraperStatsEndpoint {

    private final YTChatScraperService ytChatScraperService;
    private final KeywordRankingService keywordRankingService;

    @ReadOperation
    public Map<String, Object> getScraperStats() {
        // Initialize system statistics
        OperatingSystemMXBean osBean =
                (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        double cpuUsage = osBean.getCpuLoad() * 100.0; // System load average (better for long-term trends)
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double usedMemoryMB = usedMemory / (1024.0 * 1024.0);

        // Count running scrapers
        int runningScraperCount = 0;

        // read everything into a list so we can custom-sort
        List<Map.Entry<String, ScraperMetrics>> statsList = new ArrayList<>();
        for (Map.Entry<String, ScraperState> entry : ytChatScraperService.getScraperStates().entrySet()) {
            String videoId = entry.getKey();
            ScraperState state = entry.getValue();

            if (state.getStatus().name().equals("RUNNING")) {
                runningScraperCount++;
            }

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

            statsList.add(new AbstractMap.SimpleEntry<>(videoId, metrics));
        }

        // Now sort the list with a custom comparator:
        // 1) place FAILED at the bottom
        // 2) then compare averageThroughput descending
        // 3) then compare totalMessages descending
        statsList.sort((e1, e2) -> {
            ScraperMetrics m1 = e1.getValue();
            ScraperMetrics m2 = e2.getValue();

            // Push "FAILED" to the bottom
            boolean m1Failed = "FAILED".equalsIgnoreCase(String.valueOf(m1.status()));
            boolean m2Failed = "FAILED".equalsIgnoreCase(String.valueOf(m2.status()));
            if (m1Failed && !m2Failed) {
                return 1; // m1 goes after m2
            } else if (!m1Failed && m2Failed) {
                return -1; // m1 goes before m2
            }
            // If both are (or aren't) FAILED, compare averageThroughput descending
            int cmpAvg = Double.compare(m2.averageThroughput(), m1.averageThroughput());
            if (cmpAvg != 0) {
                return cmpAvg;
            }
            // If averageThroughput is the same, compare totalMessages descending
            return Long.compare(m2.totalMessages(), m1.totalMessages());
        });

        // Construct a LinkedHashMap to preserve the sorted order
        Map<String, ScraperMetrics> sortedStats = new LinkedHashMap<>();
        for (Map.Entry<String, ScraperMetrics> entry : statsList) {
            sortedStats.put(entry.getKey(), entry.getValue());
        }

        // Wrap everything in a root-level response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("cpuUsage", cpuUsage);
        response.put("memUsage", usedMemoryMB);
        response.put("runningScraperCount", runningScraperCount);
        response.put("scrapers", sortedStats);

        return response;
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
