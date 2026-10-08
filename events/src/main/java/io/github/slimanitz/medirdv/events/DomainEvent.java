package io.github.slimanitz.medirdv.events;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Marker for event payloads. Payloads only carry identifiers and time slots, never names,
 * e-mails or phone numbers (see docs/rgpd.md).
 */
public interface DomainEvent {

    /** Aggregate type, used to route the event to its topic. */
    @JsonIgnore
    String aggregateType();

    /** Kafka record key: guarantees ordering per aggregate. */
    @JsonIgnore
    String key();

    /** Event type name written in the envelope. */
    @JsonIgnore
    default String eventType() {
        return getClass().getSimpleName();
    }
}
