package io.github.slimanitz.medirdv.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.slimanitz.medirdv.events.AppointmentCancelled;
import io.github.slimanitz.medirdv.events.CancellationReason;
import io.github.slimanitz.medirdv.events.CancelledBy;
import io.github.slimanitz.medirdv.events.RejectionReason;
import io.github.slimanitz.medirdv.events.AppointmentRequested;
import io.github.slimanitz.medirdv.events.EventEnvelope;
import io.github.slimanitz.medirdv.events.EventTypes;
import io.github.slimanitz.medirdv.events.PatientDeactivated;
import io.github.slimanitz.medirdv.events.PatientRejected;
import io.github.slimanitz.medirdv.events.PatientValidated;
import io.github.slimanitz.medirdv.events.Topics;
import io.github.slimanitz.medirdv.scheduling.appointment.ValidationTimeoutJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.slimanitz.medirdv.scheduling.KafkaTestSupport.awaitEvent;
import static io.github.slimanitz.medirdv.scheduling.KafkaTestSupport.payload;
import static io.github.slimanitz.medirdv.scheduling.KafkaTestSupport.publish;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end saga test: patient-service is stubbed by producing its replies on {@code patient.events}.
 */
class BookingSagaIT extends AbstractIntegrationTest {

    private static final UUID DR_MARTIN = UUID.fromString("00000000-0000-4000-b000-000000000001");
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final AtomicInteger WEEK = new AtomicInteger(1);

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ValidationTimeoutJob timeoutJob;

    /** A distinct future Monday per test, 09:00 Paris time: Dr Martin is available. */
    private static Instant freeMondaySlot() {
        LocalDate monday = LocalDate.now(PARIS).with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .plusWeeks(WEEK.getAndIncrement());
        return monday.atTime(9, 0).atZone(PARIS).toInstant();
    }

    private static RequestPostProcessor patient(UUID patientId) {
        return jwt().jwt(j -> j.claim("preferred_username", "alice").claim("patient_id", patientId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_patient"));
    }

    private static RequestPostProcessor practitioner() {
        return jwt().jwt(j -> j.claim("preferred_username", "dr.martin").claim("practitioner_id", DR_MARTIN.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_practitioner"));
    }

    private String kafka() {
        return KAFKA.getBootstrapServers();
    }

    private UUID book(UUID patientId, Instant start) throws Exception {
        String body = mvc.perform(post("/appointments").with(patient(patientId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"practitionerId":"%s","start":"%s","reason":"Renouvellement d'ordonnance"}
                                """.formatted(DR_MARTIN, start)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_VALIDATION"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private String statusOf(UUID appointmentId, UUID patientId) throws Exception {
        String body = mvc.perform(get("/appointments/{id}", appointmentId).with(patient(patientId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("status").asText();
    }

    private static boolean about(EventEnvelope envelope, String type, UUID appointmentId) {
        return envelope.type().equals(type)
                && envelope.payload().path("appointmentId").asText().equals(appointmentId.toString());
    }

    @Test
    void bookingIsConfirmedWhenPatientIsValidatedThenCancelled() throws Exception {
        UUID patientId = UUID.randomUUID();
        Instant start = freeMondaySlot();

        UUID appointmentId = book(patientId, start);

        // Step 1: AppointmentRequested leaves through the outbox, without personal data.
        EventEnvelope requested = awaitEvent(kafka(), Topics.APPOINTMENT_EVENTS,
                e -> about(e, EventTypes.APPOINTMENT_REQUESTED, appointmentId));
        AppointmentRequested payload = payload(requested, AppointmentRequested.class);
        assertThat(payload.patientId()).isEqualTo(patientId);
        assertThat(payload.slotStart()).isEqualTo(start);
        assertThat(requested.payload().toString()).doesNotContain("Renouvellement");

        // Step 2 (stubbed patient-service), delivered twice to prove idempotency.
        EventEnvelope validated = publish(kafka(), new PatientValidated(appointmentId, patientId));
        publishRaw(validated);

        // Step 3: confirmed.
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(statusOf(appointmentId, patientId)).isEqualTo("CONFIRMED"));
        awaitEvent(kafka(), Topics.APPOINTMENT_EVENTS, e -> about(e, EventTypes.APPOINTMENT_CONFIRMED, appointmentId));
        assertThat(jdbc.queryForObject("select count(*) from processed_events where event_id = ?", Long.class,
                validated.eventId())).isEqualTo(1L);

        // The slot is no longer offered.
        LocalDate day = start.atZone(PARIS).toLocalDate();
        String slots = mvc.perform(get("/practitioners/{id}/slots", DR_MARTIN).with(patient(patientId))
                        .param("from", day.toString()).param("to", day.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(slots).doesNotContain(start.toString());

        // Cancellation by the practitioner.
        mvc.perform(delete("/appointments/{id}", appointmentId).with(practitioner())
                        .param("reasonCode", "PRACTITIONER_UNAVAILABLE").param("comment", "Congrès à Lyon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        EventEnvelope cancelled = awaitEvent(kafka(), Topics.APPOINTMENT_EVENTS,
                e -> about(e, EventTypes.APPOINTMENT_CANCELLED, appointmentId));
        AppointmentCancelled cancelledPayload = payload(cancelled, AppointmentCancelled.class);
        assertThat(cancelledPayload.cancelledBy()).isEqualTo(CancelledBy.PRACTITIONER);
        assertThat(cancelledPayload.reasonCode()).isEqualTo(CancellationReason.PRACTITIONER_UNAVAILABLE);
        // The free-text comment stays in the scheduling database.
        assertThat(cancelled.payload().toString()).doesNotContain("Lyon");
        mvc.perform(get("/appointments/{id}", appointmentId).with(practitioner()))
                .andExpect(jsonPath("$.statusComment").value("Congrès à Lyon"));
    }

    private void publishRaw(EventEnvelope envelope) {
        try (var producer = new org.apache.kafka.clients.producer.KafkaProducer<String, String>(java.util.Map.of(
                "bootstrap.servers", kafka(),
                "key.serializer", org.apache.kafka.common.serialization.StringSerializer.class,
                "value.serializer", org.apache.kafka.common.serialization.StringSerializer.class))) {
            producer.send(new org.apache.kafka.clients.producer.ProducerRecord<>(Topics.PATIENT_EVENTS,
                    envelope.payload().path("appointmentId").asText(),
                    new io.github.slimanitz.medirdv.events.EventCodec().write(envelope))).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void rejectedPatientCompensatesAndReleasesTheSlot() throws Exception {
        UUID patientId = UUID.randomUUID();
        Instant start = freeMondaySlot();
        UUID appointmentId = book(patientId, start);

        publish(kafka(), new PatientRejected(appointmentId, patientId, RejectionReason.PATIENT_INACTIVE));

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(statusOf(appointmentId, patientId)).isEqualTo("REJECTED"));
        awaitEvent(kafka(), Topics.APPOINTMENT_EVENTS, e -> about(e, EventTypes.APPOINTMENT_REJECTED, appointmentId));
        // Released: another patient can book the same slot.
        book(UUID.randomUUID(), start);
    }

    @Test
    void doubleBookingOfTheSameSlotIsRefused() throws Exception {
        Instant start = freeMondaySlot();
        book(UUID.randomUUID(), start);

        mvc.perform(post("/appointments").with(patient(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"practitionerId\":\"%s\",\"start\":\"%s\"}".formatted(DR_MARTIN, start)))
                .andExpect(status().isConflict());
    }

    @Test
    void pendingAppointmentsAreRejectedAfterTheTimeout() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = book(patientId, freeMondaySlot());
        jdbc.update("update appointments set created_at = now() - interval '10 minutes' where id = ?", appointmentId);

        timeoutJob.rejectExpired();

        assertThat(statusOf(appointmentId, patientId)).isEqualTo("REJECTED");
        EventEnvelope rejected = awaitEvent(kafka(), Topics.APPOINTMENT_EVENTS,
                e -> about(e, EventTypes.APPOINTMENT_REJECTED, appointmentId));
        assertThat(rejected.payload().path("reason").asText()).isEqualTo("VALIDATION_TIMEOUT");
    }

    @Test
    void deactivatedPatientHasFutureAppointmentsCancelled() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = book(patientId, freeMondaySlot());
        publish(kafka(), new PatientValidated(appointmentId, patientId));
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(statusOf(appointmentId, patientId)).isEqualTo("CONFIRMED"));

        publish(kafka(), new PatientDeactivated(patientId));

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(statusOf(appointmentId, patientId)).isEqualTo("CANCELLED"));
    }

    @Test
    void confirmedAppointmentCanBeRescheduledToAnotherFreeSlot() throws Exception {
        UUID patientId = UUID.randomUUID();
        Instant start = freeMondaySlot();
        UUID appointmentId = book(patientId, start);
        publish(kafka(), new PatientValidated(appointmentId, patientId));
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(statusOf(appointmentId, patientId)).isEqualTo("CONFIRMED"));
        Instant newStart = start.plusSeconds(3600);

        mvc.perform(patch("/appointments/{id}", appointmentId).with(patient(patientId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start\":\"%s\"}".formatted(newStart)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start").value(newStart.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        EventEnvelope moved = awaitEvent(kafka(), Topics.APPOINTMENT_EVENTS,
                e -> about(e, EventTypes.APPOINTMENT_RESCHEDULED, appointmentId));
        assertThat(moved.payload().path("previousStart").asText()).isEqualTo(start.toString());
        // The old slot is free again: another patient can take it.
        book(UUID.randomUUID(), start);
    }

    private static RequestPostProcessor practitionerWithoutRecord() {
        return jwt().jwt(j -> j.claim("preferred_username", "dr.nobody"))
                .authorities(new SimpleGrantedAuthority("ROLE_practitioner"));
    }

    private static RequestPostProcessor otherPractitioner() {
        return jwt().jwt(j -> j.claim("practitioner_id", "00000000-0000-4000-b000-000000000002"))
                .authorities(new SimpleGrantedAuthority("ROLE_practitioner"));
    }

    @Test
    void accessChecksFailClosedAndListsAreScoped() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID appointmentId = book(owner, freeMondaySlot());

        // A practitioner account without practitioner_id, or another practitioner: refused.
        mvc.perform(get("/appointments/{id}", appointmentId).with(practitionerWithoutRecord()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Accès refusé à ce rendez-vous"));
        mvc.perform(get("/appointments/{id}", appointmentId).with(otherPractitioner()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/appointments/{id}", appointmentId).with(otherPractitioner()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/appointments").with(practitionerWithoutRecord()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/appointments").with(otherPractitioner()).param("practitionerId", DR_MARTIN.toString()))
                .andExpect(status().isForbidden());

        // Dr Martin sees the appointment in his agenda and in the patient's history with him.
        mvc.perform(get("/appointments").with(practitioner()).param("patientId", owner.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(appointmentId.toString()));
        // Another practitioner asking for the same patient only gets their own appointments (none).
        mvc.perform(get("/appointments").with(otherPractitioner()).param("patientId", owner.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        // A patient's practitionerId / patientId parameters are ignored: only their own appointments.
        mvc.perform(get("/appointments").with(patient(UUID.randomUUID())).param("patientId", owner.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        UUID patientId = UUID.randomUUID();
        LocalDate from = LocalDate.now(PARIS).plusDays(1);
        mvc.perform(get("/practitioners/{id}/slots", DR_MARTIN).with(patient(patientId))
                        .param("from", from.toString()).param("to", from.plusDays(30).toString()))
                .andExpect(status().isOk());
        mvc.perform(get("/practitioners/{id}/slots", DR_MARTIN).with(patient(patientId))
                        .param("from", from.toString()).param("to", from.plusDays(31).toString()))
                .andExpect(status().isBadRequest());

        UUID appointmentId = book(patientId, freeMondaySlot());
        mvc.perform(delete("/appointments/{id}", appointmentId).with(patient(patientId))
                        .param("comment", "x".repeat(501)))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/appointments/{id}", appointmentId).with(patient(patientId))
                        .param("reasonCode", "PATIENT_DEACTIVATED"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void databaseRefusesOverlappingActiveAppointments() {
        Instant start = freeMondaySlot();
        String insert = """
                insert into appointments (id, patient_id, practitioner_id, start_at, end_at, status, created_at,
                                          updated_at, version)
                values (?, ?, ?, ?, ?, ?, now(), now(), 0)
                """;
        jdbc.update(insert, UUID.randomUUID(), UUID.randomUUID(), DR_MARTIN, Timestamp.from(start),
                Timestamp.from(start.plusSeconds(1800)), "CONFIRMED");

        // Different start time (not caught by the unique index) but overlapping range.
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), UUID.randomUUID(), DR_MARTIN,
                Timestamp.from(start.plusSeconds(900)), Timestamp.from(start.plusSeconds(2700)), "PENDING_VALIDATION"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_appointments_no_overlap");
        // Inactive appointments do not hold the range.
        jdbc.update(insert, UUID.randomUUID(), UUID.randomUUID(), DR_MARTIN,
                Timestamp.from(start.plusSeconds(900)), Timestamp.from(start.plusSeconds(2700)), "CANCELLED");
    }

    @Test
    void unreadableMessagesGoToTheDeadLetterTopic() throws Exception {
        String key = "poison-" + UUID.randomUUID();
        try (var producer = new org.apache.kafka.clients.producer.KafkaProducer<String, String>(java.util.Map.of(
                "bootstrap.servers", kafka(),
                "key.serializer", org.apache.kafka.common.serialization.StringSerializer.class,
                "value.serializer", org.apache.kafka.common.serialization.StringSerializer.class))) {
            producer.send(new org.apache.kafka.clients.producer.ProducerRecord<>(Topics.PATIENT_EVENTS, key,
                    "{not json")).get();
        }

        try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(java.util.Map.of(
                "bootstrap.servers", kafka(), "group.id", "test-" + UUID.randomUUID(),
                "auto.offset.reset", "earliest",
                "key.deserializer", org.apache.kafka.common.serialization.StringDeserializer.class,
                "value.deserializer", org.apache.kafka.common.serialization.StringDeserializer.class))) {
            consumer.subscribe(java.util.List.of(Topics.PATIENT_EVENTS_DLT));
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        return true;
                    }
                }
                return false;
            });
        }
        // The partition is not blocked: the saga still works afterwards.
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = book(patientId, freeMondaySlot());
        publish(kafka(), new PatientValidated(appointmentId, patientId));
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(statusOf(appointmentId, patientId)).isEqualTo("CONFIRMED"));
    }

    @Test
    void securityRulesAreEnforced() throws Exception {
        mvc.perform(get("/practitioners")).andExpect(status().isUnauthorized());

        UUID owner = UUID.randomUUID();
        UUID appointmentId = book(owner, freeMondaySlot());
        mvc.perform(get("/appointments/{id}", appointmentId).with(patient(UUID.randomUUID())))
                .andExpect(status().isForbidden());

        String list = mvc.perform(get("/practitioners").with(patient(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode practitioners = json.readTree(list);
        assertThat(practitioners).hasSize(3);
    }
}
