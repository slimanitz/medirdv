package io.github.slimanitz.medirdv.scheduling.practitioner;

import java.time.Instant;

/** A bookable time range. */
public record Slot(Instant start, Instant end) {

    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }
}
