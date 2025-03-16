package csw.youtube.chat.live.dto;

import com.github.pemistahl.lingua.api.Language;
import csw.youtube.chat.live.model.ScraperState;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ScraperMetrics(
                String videoTitle,
                String channelName,
                String videoUrl,
                ScraperState.Status status,
                long runningTimeMinutes, // minutes
                Set<Language> skipLangs,
                List<TopChatter> topChatters,
                List<RecentDonator> recentDonations,
                long lastThroughput,
                long maxThroughput,
                double averageThroughput,
                long totalMessages,
                List<KeywordRankingPair> topKeywords,
                Map<String, Double> topLanguages,
                String threadName,
                Instant createdAt,
                Instant finishedAt,
                String errorMessage) {
}