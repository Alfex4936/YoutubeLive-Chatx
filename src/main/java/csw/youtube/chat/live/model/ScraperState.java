package csw.youtube.chat.live.model;

import csw.youtube.chat.live.service.YTChatScraperService;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Data
@NoArgsConstructor
public class ScraperState {

    private String videoId;
    private Status status = Status.IDLE;
    private String threadName;
    private String errorMessage;

    private String videoTitle;
    private String channelName;
    private Instant createdAt;
    // Counts all messages since scraper started
    private AtomicLong totalMessages = new AtomicLong(0);
    //  Throughput metrics
    private volatile int lastThroughput;     // messages in the last 10s interval
    private volatile int maxThroughput;      // highest count in any 10s window so far
    private volatile double averageThroughput; // rolling average across intervals
    // Max: 9,223,372,036,854,775,807 (9.2 quintillion (10^18) so I don't expect overflowing)
    // If a video gets 1000 messages per second
    // 86.4 million messages per day that will take 292k years to overflow.
    private AtomicLong intervalsCount = new AtomicLong(0);

    public ScraperState(String videoId) {
        this.videoId = videoId;
    }

    public String getVideoUrl() {
        return YTChatScraperService.YOUTUBE_WATCH_URL + videoId;
    }

    public enum Status {
        IDLE, RUNNING, FAILED, COMPLETED
    }
}
