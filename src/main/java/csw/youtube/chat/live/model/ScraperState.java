package csw.youtube.chat.live.model;

import com.github.pemistahl.lingua.api.Language;
import csw.youtube.chat.live.dto.RecentDonator;
import csw.youtube.chat.live.dto.SimpleChatMessage;
import csw.youtube.chat.live.dto.TopChatter;
import csw.youtube.chat.live.service.YTRustScraperService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
public class ScraperState {

    private static final Duration MESSAGE_RETENTION_DURATION = Duration.ofMinutes(5);
    private String videoId;
    private Status status = Status.IDLE;
    private String threadName;
    private String videoTitle;
    private String channelName;
    private Instant createdAt;
    private Instant finishedAt;
    private String reason;

    private List<TopChatter> topChatters = new ArrayList<>();
    private List<RecentDonator> recentDonations = new ArrayList<>();
    private Deque<ChatMessageWithTimestamp> recentMessages = new ConcurrentLinkedDeque<>();
    // Counts all messages since scraper started
    private AtomicLong totalMessages = new AtomicLong(0);
    // Throughput metrics
    private volatile long lastThroughput; // messages in the last 10s interval
    private volatile long maxThroughput; // highest count in any 10s window so far
    private volatile double averageThroughput; // rolling average across intervals
    // Max: 9,223,372,036,854,775,807 (9.2 quintillion (10^18) so I don't expect
    // overflowing)
    // If a video gets 1000 messages per second
    // 86.4 million messages per day that will take 292k years to overflow.
    private AtomicLong intervalsCount = new AtomicLong(0);

    private Set<Language> skipLangs;

    public ScraperState(String videoId) {
        this.videoId = videoId;
    }

    public ScraperState(String videoId, Set<Language> skipLangs) {
        this.videoId = videoId;
        this.skipLangs = skipLangs;
    }

    public String getVideoUrl() {
        return YTRustScraperService.YOUTUBE_WATCH_URL + videoId;
    }

    public boolean isActiveOrDead() {
        return this.status == Status.RUNNING || this.status == Status.COMPLETED || this.status == Status.FAILED;
    }

    public void addRecentMessages(List<SimpleChatMessage> messages) {
        long now = System.currentTimeMillis();
        messages.forEach(msg -> recentMessages.addLast(new ChatMessageWithTimestamp(msg, now)));

        cleanupOldMessages();
    }

    private void cleanupOldMessages() {
        long cutoff = System.currentTimeMillis() - MESSAGE_RETENTION_DURATION.toMillis();
        while (!recentMessages.isEmpty() && recentMessages.peekFirst().timestamp < cutoff) {
            recentMessages.pollFirst();
        }
    }

    public String getCombinedRecentMessages() {
        return recentMessages.stream()
                .skip(Math.max(0, recentMessages.size() - 100))  // Keep only last 100 messages
                .map(m -> m.message.message())
                .collect(Collectors.joining("\n"));
    }

    public enum Status {
        QUEUED, IDLE, RUNNING, FAILED, COMPLETED
    }

    @Data
    @AllArgsConstructor
    public static class ChatMessageWithTimestamp {
        private SimpleChatMessage message;
        private long timestamp;
    }
}
