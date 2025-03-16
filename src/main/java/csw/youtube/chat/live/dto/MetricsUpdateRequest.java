package csw.youtube.chat.live.dto;

import java.time.Instant;
import java.util.List;

public record MetricsUpdateRequest(String videoTitle, String channelName, String videoId, Instant createdAt,
        List<String> skipLangs, long messagesInLastInterval, long totalMessages, String status,
        List<TopChatter> topChatters, List<RecentDonator> recentDonations) {
}
