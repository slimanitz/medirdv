package io.github.slimanitz.medirdv.platform.outbox;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * JDBC access to the {@code outbox} table (V1 + V2 Flyway migrations of each producing service).
 * Status: {@code PENDING} (to publish), {@code SENT}, {@code FAILED} (parked poison message).
 */
public class OutboxRepository {

    /** Arbitrary constant identifying the publisher lock (pg advisory locks are database-wide). */
    static final long PUBLISHER_LOCK_ID = 0x4d65646952445601L;

    private final JdbcTemplate jdbc;

    public OutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(OutboxMessage message) {
        jdbc.update("""
                INSERT INTO outbox (id, aggregate_type, aggregate_id, type, payload)
                VALUES (?, ?, ?, ?, ?::jsonb)
                """, message.id(), message.aggregateType(), message.aggregateId(), message.type(), message.payload());
    }

    /**
     * Transaction-scoped advisory lock: only one replica publishes at a time, which keeps the
     * per-aggregate order of {@code created_at} even when the service is scaled out.
     */
    public boolean tryAcquirePublisherLock() {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, PUBLISHER_LOCK_ID));
    }

    /** Locks the oldest pending messages ({@code SKIP LOCKED} as a second safety net). */
    public List<OutboxMessage> lockPending(int limit) {
        return jdbc.query("""
                SELECT id, aggregate_type, aggregate_id, type, payload::text AS payload, attempts
                FROM outbox
                WHERE status = 'PENDING'
                ORDER BY created_at, id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """, (rs, i) -> new OutboxMessage(rs.getObject("id", UUID.class), rs.getString("aggregate_type"),
                rs.getString("aggregate_id"), rs.getString("type"), rs.getString("payload"),
                rs.getInt("attempts")), limit);
    }

    public void markSent(List<UUID> ids) {
        jdbc.batchUpdate("UPDATE outbox SET status = 'SENT', sent_at = now() WHERE id = ?", ids, ids.size(),
                (ps, id) -> ps.setObject(1, id));
    }

    /** Records a failed attempt; {@code park} moves the message out of the queue (status FAILED). */
    public void recordFailure(UUID id, String error, boolean park) {
        jdbc.update("""
                UPDATE outbox
                SET attempts = attempts + 1, last_error = left(?, 1000),
                    status = CASE WHEN ? THEN 'FAILED' ELSE status END
                WHERE id = ?
                """, error, park, id);
    }

    public long countPending() {
        return count("PENDING");
    }

    public long countFailed() {
        return count("FAILED");
    }

    private long count(String status) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE status = ?", Long.class, status);
        return count == null ? 0 : count;
    }
}
