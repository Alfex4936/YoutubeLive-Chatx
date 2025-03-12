package csw.youtube.chat.live.dto;

import com.github.pemistahl.lingua.api.Language;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record MetricsUpdateRequest(String videoTitle, String channelName, String videoId, Instant createdAt,
                                   List<String> skipLangs, int messagesInLastInterval, String status, List<TopChatter> topChatters) {
}
