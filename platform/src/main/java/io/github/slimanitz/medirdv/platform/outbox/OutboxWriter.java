package io.github.slimanitz.medirdv.platform.outbox;

import io.github.slimanitz.medirdv.events.DomainEvent;
import io.github.slimanitz.medirdv.events.EventCodec;
import io.github.slimanitz.medirdv.events.EventEnvelope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a domain event in the outbox table. Must be called inside the business transaction:
 * the state change and the event are committed (or rolled back) together.
 */
public class OutboxWriter {

    private final OutboxRepository repository;
    private final EventCodec codec;
    private final Tracer tracer;

    public OutboxWriter(OutboxRepository repository, EventCodec codec, Tracer tracer) {
        this.repository = repository;
        this.codec = codec;
        this.tracer = tracer;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public EventEnvelope record(DomainEvent event) {
        EventEnvelope envelope = codec.wrap(event, currentTraceparent());
        repository.insert(new OutboxMessage(envelope.eventId(), event.aggregateType(), event.key(),
                envelope.type(), codec.write(envelope)));
        return envelope;
    }

    /** W3C traceparent of the current span, so the consumer joins the same trace. */
    String currentTraceparent() {
        Span span = tracer.currentSpan();
        if (span == null || span.context() == null || span.context().traceId() == null) {
            return null;
        }
        boolean sampled = Boolean.TRUE.equals(span.context().sampled());
        return "00-" + span.context().traceId() + "-" + span.context().spanId() + (sampled ? "-01" : "-00");
    }
}
