package io.github.slimanitz.medirdv.platform.idempotency;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Idempotent consumer helper backed by the {@code processed_events} table. Call it in the same
 * transaction as the side effects of the event: a redelivered event is detected and skipped.
 */
public class ProcessedEvents {

    private final JdbcTemplate jdbc;

    public ProcessedEvents(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return {@code true} the first time an event id is seen, {@code false} for a duplicate. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(UUID eventId) {
        int inserted = jdbc.update(
                "INSERT INTO processed_events (event_id, processed_at) VALUES (?, now()) ON CONFLICT DO NOTHING",
                eventId);
        return inserted == 1;
    }
}
