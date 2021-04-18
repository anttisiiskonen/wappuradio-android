package fi.wappuradio.wappuradio;

import java.time.Instant;

public class WappuradioProgram {
    private final Instant startTime;
    private final Instant endTime;
    private final String title;

    public WappuradioProgram(Instant startTime, Instant endTime, String title) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.title = title;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public String getTitle() {
        return title;
    }
}
