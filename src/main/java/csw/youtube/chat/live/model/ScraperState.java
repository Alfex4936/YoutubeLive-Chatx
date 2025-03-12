package csw.youtube.chat.live.model;

import com.github.pemistahl.lingua.api.Language;
import csw.youtube.chat.live.dto.TopChatter;
import csw.youtube.chat.live.service.YTRustScraperService;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    private List<TopChatter> topChatters = new ArrayList<>();

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

    public enum Status {
        QUEUED, IDLE, RUNNING, FAILED, COMPLETED
    }

    public boolean isActiveOrDead() {
        return this.status == Status.RUNNING || this.status == Status.COMPLETED || this.status == Status.FAILED;
    }
}
