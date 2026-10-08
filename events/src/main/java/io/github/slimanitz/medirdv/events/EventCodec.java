package io.github.slimanitz.medirdv.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Serialises events to and from their JSON envelope. Thread-safe. */
public final class EventCodec {

    private final ObjectMapper mapper;
    private final Clock clock;

    public EventCodec() {
        this(Clock.systemUTC());
    }

    public EventCodec(Clock clock) {
        this.clock = clock;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /** Wraps a payload in a new envelope with a fresh event id. */
    public EventEnvelope wrap(DomainEvent event, String traceparent) {
        return new EventEnvelope(UUID.randomUUID(), event.eventType(), Instant.now(clock), traceparent,
                mapper.valueToTree(event));
    }

    public String write(EventEnvelope envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException("Cannot serialise event " + envelope.type(), e);
        }
    }

    public EventEnvelope read(String json) {
        try {
            EventEnvelope envelope = mapper.readValue(json, EventEnvelope.class);
            if (envelope.eventId() == null || envelope.type() == null) {
                throw new EventSerializationException("Envelope without eventId or type", null);
            }
            return envelope;
        } catch (JsonProcessingException e) {
            throw new EventSerializationException("Cannot deserialise event", e);
        }
    }

    public <T extends DomainEvent> T payload(EventEnvelope envelope, Class<T> type) {
        try {
            return mapper.treeToValue(envelope.payload(), type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new EventSerializationException("Cannot read payload of " + envelope.type(), e);
        }
    }

    /** Thrown when an event cannot be (de)serialised; never retried successfully. */
    public static class EventSerializationException extends RuntimeException {
        public EventSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
