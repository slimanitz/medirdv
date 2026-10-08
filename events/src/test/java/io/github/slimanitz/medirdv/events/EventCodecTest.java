package io.github.slimanitz.medirdv.events;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventCodecTest {

    private final Instant now = Instant.parse("2026-10-12T07:00:00Z");
    private final EventCodec codec = new EventCodec(Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void roundTripsAnAppointmentRequestedThroughTheEnvelope() {
        var event = new AppointmentRequested(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-10-12T07:00:00Z"), Instant.parse("2026-10-12T07:30:00Z"));

        String json = codec.write(codec.wrap(event, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"));
        EventEnvelope envelope = codec.read(json);

        assertThat(envelope.type()).isEqualTo(EventTypes.APPOINTMENT_REQUESTED);
        assertThat(envelope.occurredAt()).isEqualTo(now);
        assertThat(envelope.traceparent()).startsWith("00-4bf92f");
        assertThat(codec.payload(envelope, AppointmentRequested.class)).isEqualTo(event);
    }

    @Test
    void writesIsoDatesAndNoInternalAccessors() {
        var event = new PatientValidated(UUID.randomUUID(), UUID.randomUUID());

        String json = codec.write(codec.wrap(event, null));

        assertThat(json).contains("\"occurredAt\":\"2026-10-12T07:00:00Z\"")
                .doesNotContain("traceparent")
                .doesNotContain("aggregateType")
                .doesNotContain("\"key\"");
    }

    private static final Class<?>[] PAYLOADS = {AppointmentRequested.class, AppointmentConfirmed.class,
            AppointmentRejected.class, AppointmentCancelled.class, AppointmentRescheduled.class,
            PatientValidated.class, PatientRejected.class, PatientDeactivated.class};

    @Test
    void payloadsNeverContainPersonalData() {
        for (Class<?> type : PAYLOADS) {
            for (var component : type.getRecordComponents()) {
                assertThat(component.getName().toLowerCase())
                        .as("%s.%s", type.getSimpleName(), component.getName())
                        .doesNotContain("name").doesNotContain("email").doesNotContain("phone")
                        .doesNotContain("birth").doesNotContain("comment");
            }
        }
    }

    /** Free text may contain health information: payloads only allow ids, instants and enums. */
    @Test
    void payloadsHaveNoFreeTextFields() {
        for (Class<?> type : PAYLOADS) {
            for (var component : type.getRecordComponents()) {
                assertThat(component.getType() == UUID.class || component.getType() == Instant.class
                        || component.getType().isEnum())
                        .as("%s.%s must be an id, an instant or an enum", type.getSimpleName(), component.getName())
                        .isTrue();
            }
        }
    }

    @Test
    void codesAreSerialisedAsPlainStrings() {
        var event = new AppointmentCancelled(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-10-12T07:00:00Z"), CancelledBy.PRACTITIONER,
                CancellationReason.PRACTITIONER_UNAVAILABLE);

        EventEnvelope envelope = codec.read(codec.write(codec.wrap(event, null)));

        assertThat(envelope.payload().path("reasonCode").asText()).isEqualTo("PRACTITIONER_UNAVAILABLE");
        assertThat(codec.payload(envelope, AppointmentCancelled.class)).isEqualTo(event);
    }

    @Test
    void routesAggregatesToTheirTopic() {
        assertThat(Topics.forAggregate(new PatientDeactivated(UUID.randomUUID()).aggregateType()))
                .isEqualTo(Topics.PATIENT_EVENTS);
        assertThat(Topics.forAggregate(AggregateTypes.APPOINTMENT)).isEqualTo(Topics.APPOINTMENT_EVENTS);
        assertThatThrownBy(() -> Topics.forAggregate("Unknown")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedMessages() {
        assertThatThrownBy(() -> codec.read("not-json"))
                .isInstanceOf(EventCodec.EventSerializationException.class);
        assertThatThrownBy(() -> codec.read("{\"type\":\"X\"}"))
                .isInstanceOf(EventCodec.EventSerializationException.class);
    }
}
