package io.github.slimanitz.medirdv.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope written on every topic.
 *
 * @param eventId     unique id, used by consumers for idempotency
 * @param type        event type (see {@link EventTypes})
 * @param occurredAt  when the event was recorded in the producer transaction
 * @param traceparent W3C trace context captured when the event was written to the outbox
 * @param payload     event payload (identifiers only)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope(UUID eventId, String type, Instant occurredAt, String traceparent, JsonNode payload) {
}
