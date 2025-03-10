package csw.youtube.chat.playwright;

import com.github.pemistahl.lingua.api.Language;
import com.sun.management.OperatingSystemMXBean;
import csw.youtube.chat.live.dto.KeywordRankingPair;
import csw.youtube.chat.live.model.ScraperState;
import csw.youtube.chat.live.service.RankingService;
import csw.youtube.chat.live.service.YTRustScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

// GET http://localhost:8080/actuator/scraperStats
@Component
@Endpoint(id = "scraperStats")
@RequiredArgsConstructor
public class ScraperStatsEndpoint {

    private final YTRustScraperService ytRustScraperService;
    private final RankingService rankingService;

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
        for (Map.Entry<String, ScraperState> entry : ytRustScraperService.getScraperStates().entrySet()) {
            String videoId = entry.getKey();
            ScraperState state = entry.getValue();

            boolean isRunning = state.getStatus().name().equals("RUNNING");

            if (isRunning) {
                runningScraperCount++;
            }

            Map<String, Double> topLanguages = rankingService.getTopLanguages(videoId, 3);

            // Retrieve top 5 keywords
            List<KeywordRankingPair> topKeywordsWithScores = rankingService.getTopKeywordStrings(videoId, 5);

            long runningTimeMinutes = 0;
            if (isRunning) {
                Instant createdAt = state.getCreatedAt();
                runningTimeMinutes = Duration.between(createdAt, Instant.now()).toMinutes();
            }

            ScraperMetrics metrics = new ScraperMetrics(
                    state.getVideoTitle(),
                    state.getChannelName(),
                    state.getVideoUrl(),
                    state.getStatus(),
                    runningTimeMinutes,
                    state.getSkipLangs(),
                    state.getLastThroughput(),
                    state.getMaxThroughput(),
                    state.getAverageThroughput(),
                    state.getTotalMessages().get(),
                    topKeywordsWithScores,
                    topLanguages, // %
                    state.getThreadName(),
                    state.getCreatedAt(),
                    state.getErrorMessage()
            );

            statsList.add(new AbstractMap.SimpleEntry<>(videoId, metrics));
        }

        // Now sort the list with a custom comparator:
        // 1) place COMPLETED/FAILED at the bottom
        // 2) then compare averageThroughput descending
        // 3) then compare totalMessages descending
        statsList.sort((e1, e2) -> {
            ScraperMetrics m1 = e1.getValue();
            ScraperMetrics m2 = e2.getValue();

            String status1 = String.valueOf(m1.status()).toUpperCase();
            String status2 = String.valueOf(m2.status()).toUpperCase();

            boolean m1Failed = "FAILED".equals(status1);
            boolean m2Failed = "FAILED".equals(status2);
            boolean m1Completed = "COMPLETED".equals(status1);
            boolean m2Completed = "COMPLETED".equals(status2);

            // Push "FAILED" to the bottom
            if (m1Failed && !m2Failed) return 1;
            if (!m1Failed && m2Failed) return -1;

            // Push "COMPLETED" below everything except "FAILED"
            if (m1Completed && !m2Completed) return 1;
            if (!m1Completed && m2Completed) return -1;

            // If statuses are equal or neither is COMPLETED/FAILED, sort by averageThroughput (descending)
            int cmpAvg = Double.compare(m2.averageThroughput(), m1.averageThroughput());
            if (cmpAvg != 0) return cmpAvg;

            // If averageThroughput is the same, compare totalMessages (descending)
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
            long runningTimeMinutes, // minutes
            Set<Language> skipLangs,
            int lastThroughput,
            int maxThroughput,
            double averageThroughput,
            long totalMessages,
            List<KeywordRankingPair> topKeywords,
            Map<String, Double> topLanguages,
            String threadName,
            Instant createdAt,
            String errorMessage
    ) {
    }
}
