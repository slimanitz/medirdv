package io.github.slimanitz.medirdv.patient;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.slimanitz.medirdv.events.AppointmentRequested;
import io.github.slimanitz.medirdv.events.EventEnvelope;
import io.github.slimanitz.medirdv.events.EventTypes;
import io.github.slimanitz.medirdv.events.PatientRejected;
import io.github.slimanitz.medirdv.events.RejectionReason;
import io.github.slimanitz.medirdv.events.Topics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.UUID;

import static io.github.slimanitz.medirdv.patient.KafkaTestSupport.awaitEvent;
import static io.github.slimanitz.medirdv.patient.KafkaTestSupport.payload;
import static io.github.slimanitz.medirdv.patient.KafkaTestSupport.publish;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PatientIT extends AbstractIntegrationTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-4000-a000-000000000001");
    private static final UUID JEAN_INACTIVE = UUID.fromString("00000000-0000-4000-a000-000000000004");

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private static RequestPostProcessor practitioner() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_practitioner"));
    }

    private static RequestPostProcessor patient(UUID patientId) {
        return jwt().jwt(j -> j.claim("patient_id", patientId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_patient"));
    }

    private UUID createPatient(String email) throws Exception {
        String body = mvc.perform(post("/patients").with(practitioner()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Lucie","lastName":"Bernard","email":"%s",
                                 "phone":"06 00 00 00 01","birthDate":"1979-05-30"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private static boolean about(EventEnvelope e, String type, UUID appointmentId) {
        return e.type().equals(type) && e.payload().path("appointmentId").asText().equals(appointmentId.toString());
    }

    private static AppointmentRequested request(UUID patientId) {
        return new AppointmentRequested(UUID.randomUUID(), patientId, UUID.randomUUID(),
                Instant.parse("2027-01-04T08:00:00Z"), Instant.parse("2027-01-04T08:30:00Z"));
    }

    @Test
    void staffManagesPatientsAndPatientsOnlySeeThemselves() throws Exception {
        UUID id = createPatient("lucie.bernard@example.fr");

        mvc.perform(get("/patients").with(practitioner())).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'lucie.bernard@example.fr')]").exists());
        mvc.perform(put("/patients/{id}", id).with(practitioner()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Lucie","lastName":"Bernard-Roux","email":"lucie.bernard@example.fr",
                                 "birthDate":"1979-05-30"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lastName").value("Bernard-Roux"));
        mvc.perform(post("/patients").with(practitioner()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"X\",\"lastName\":\"Y\",\"email\":\"LUCIE.bernard@example.fr\",\"birthDate\":\"1990-01-01\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/patients").with(practitioner()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"\",\"lastName\":\"Y\",\"email\":\"not-an-email\",\"birthDate\":\"2990-01-01\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/patients/me").with(patient(ALICE))).andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alice"));
        mvc.perform(get("/patients/{id}", id).with(patient(ALICE))).andExpect(status().isForbidden());
        mvc.perform(get("/patients").with(patient(ALICE))).andExpect(status().isForbidden());
        mvc.perform(get("/patients")).andExpect(status().isUnauthorized());
    }

    @Test
    void activePatientIsValidatedForABooking() {
        AppointmentRequested request = request(ALICE);

        publish(KAFKA.getBootstrapServers(), request);

        EventEnvelope reply = awaitEvent(KAFKA.getBootstrapServers(), Topics.PATIENT_EVENTS,
                e -> about(e, EventTypes.PATIENT_VALIDATED, request.appointmentId()));
        assertThat(reply.payload().path("patientId").asText()).isEqualTo(ALICE.toString());
    }

    @Test
    void unknownOrInactivePatientsAreRejected() {
        AppointmentRequested inactive = request(JEAN_INACTIVE);
        AppointmentRequested unknown = request(UUID.randomUUID());

        publish(KAFKA.getBootstrapServers(), inactive);
        publish(KAFKA.getBootstrapServers(), unknown);

        EventEnvelope first = awaitEvent(KAFKA.getBootstrapServers(), Topics.PATIENT_EVENTS,
                e -> about(e, EventTypes.PATIENT_REJECTED, inactive.appointmentId()));
        EventEnvelope second = awaitEvent(KAFKA.getBootstrapServers(), Topics.PATIENT_EVENTS,
                e -> about(e, EventTypes.PATIENT_REJECTED, unknown.appointmentId()));
        assertThat(payload(first, PatientRejected.class).reason()).isEqualTo(RejectionReason.PATIENT_INACTIVE);
        assertThat(payload(second, PatientRejected.class).reason()).isEqualTo(RejectionReason.UNKNOWN_PATIENT);
    }

    @Test
    void deactivationIsPublishedAndThenBookingsAreRejected() throws Exception {
        UUID id = createPatient("paul.garnier@example.fr");

        mvc.perform(post("/patients/{id}/deactivate", id).with(practitioner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));

        EventEnvelope deactivated = awaitEvent(KAFKA.getBootstrapServers(), Topics.PATIENT_EVENTS,
                e -> e.type().equals(EventTypes.PATIENT_DEACTIVATED)
                        && e.payload().path("patientId").asText().equals(id.toString()));
        assertThat(deactivated.payload().toString()).doesNotContain("paul").doesNotContain("Garnier");
    }

    @Test
    void failingMessagesAreRoutedToTheDeadLetterTopicWithoutBlockingThePartition() throws Exception {
        String key = "poison-" + UUID.randomUUID();
        // A well-formed envelope whose payload cannot be read as AppointmentRequested.
        String poison = """
                {"eventId":"%s","type":"AppointmentRequested","occurredAt":"2026-10-08T10:00:00Z",
                 "payload":{"appointmentId":"not-a-uuid"}}
                """.formatted(UUID.randomUUID());
        try (var producer = new org.apache.kafka.clients.producer.KafkaProducer<String, String>(java.util.Map.of(
                "bootstrap.servers", KAFKA.getBootstrapServers(),
                "key.serializer", org.apache.kafka.common.serialization.StringSerializer.class,
                "value.serializer", org.apache.kafka.common.serialization.StringSerializer.class))) {
            producer.send(new org.apache.kafka.clients.producer.ProducerRecord<>(Topics.APPOINTMENT_EVENTS, key,
                    poison)).get();
        }

        try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(java.util.Map.of(
                "bootstrap.servers", KAFKA.getBootstrapServers(), "group.id", "test-" + UUID.randomUUID(),
                "auto.offset.reset", "earliest",
                "key.deserializer", org.apache.kafka.common.serialization.StringDeserializer.class,
                "value.deserializer", org.apache.kafka.common.serialization.StringDeserializer.class))) {
            consumer.subscribe(java.util.List.of(Topics.APPOINTMENT_EVENTS_DLT));
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(30)).until(() -> {
                for (var record : consumer.poll(java.time.Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        return true;
                    }
                }
                return false;
            });
        }

        AppointmentRequested next = request(ALICE);
        publish(KAFKA.getBootstrapServers(), next);
        awaitEvent(KAFKA.getBootstrapServers(), Topics.PATIENT_EVENTS,
                e -> about(e, EventTypes.PATIENT_VALIDATED, next.appointmentId()));
    }
}
