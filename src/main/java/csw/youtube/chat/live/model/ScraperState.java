package csw.youtube.chat.live.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScraperState {
    public enum Status {
        RUNNING,
        FAILED,
        COMPLETED
    }

    private final String videoId;
    private volatile String threadName;
    private volatile Status status;
    private volatile String errorMessage;

    public ScraperState(String videoId) {
        this.videoId = videoId;
        this.status = Status.RUNNING; // default when we create it
    }
}
