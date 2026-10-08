package io.github.slimanitz.medirdv.platform.outbox;

import java.util.UUID;

/**
 * One row of the {@code outbox} table. {@code payload} is the full JSON event envelope,
 * {@code attempts} the number of failed publication attempts so far.
 */
public record OutboxMessage(UUID id, String aggregateType, String aggregateId, String type, String payload,
                            int attempts) {

    public OutboxMessage(UUID id, String aggregateType, String aggregateId, String type, String payload) {
        this(id, aggregateType, aggregateId, type, payload, 0);
    }
}
