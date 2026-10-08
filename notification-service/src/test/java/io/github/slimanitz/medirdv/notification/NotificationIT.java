package io.github.slimanitz.medirdv.notification;

import io.github.slimanitz.medirdv.events.AppointmentCancelled;
import io.github.slimanitz.medirdv.events.AppointmentConfirmed;
import io.github.slimanitz.medirdv.events.AppointmentRequested;
import io.github.slimanitz.medirdv.events.CancellationReason;
import io.github.slimanitz.medirdv.events.CancelledBy;
import io.github.slimanitz.medirdv.events.EventCodec;
import io.github.slimanitz.medirdv.events.EventEnvelope;
import io.github.slimanitz.medirdv.events.Topics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.slimanitz.medirdv.notification.KafkaTestSupport.publish;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    NotificationRepository repository;

    private final Instant start = Instant.parse("2027-03-01T08:00:00Z");

    private static RequestPostProcessor patient(UUID patientId) {
        return jwt().jwt(j -> j.claim("patient_id", patientId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_patient"));
    }

    private static RequestPostProcessor staff(String role, UUID practitionerId) {
        return jwt().jwt(j -> {
                    if (practitionerId != null) {
                        j.claim("practitioner_id", practitionerId.toString());
                    }
                })
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private void sendRaw(EventEnvelope envelope, String key) {
        sendRaw(new EventCodec().write(envelope), key);
    }

    private void sendRaw(String value, String key) {
        try (var producer = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(new ProducerRecord<>(Topics.APPOINTMENT_EVENTS, key, value)).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void confirmedAndCancelledAppointmentsProduceNotificationsOnce() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID practitionerId = UUID.randomUUID();

        EventEnvelope confirmed = publish(KAFKA.getBootstrapServers(),
                new AppointmentConfirmed(appointmentId, patientId, practitionerId, start, start.plusSeconds(1800)));
        sendRaw(confirmed, appointmentId.toString()); // redelivery
        publish(KAFKA.getBootstrapServers(),
                new AppointmentRequested(appointmentId, patientId, practitionerId, start, start.plusSeconds(1800)));
        publish(KAFKA.getBootstrapServers(),
                new AppointmentCancelled(appointmentId, patientId, practitionerId, start, CancelledBy.PATIENT,
                        CancellationReason.PATIENT_REQUEST));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(repository.findTop100ByPatientIdOrderByCreatedAtDesc(patientId))
                        .extracting(Notification::getType)
                        .containsExactlyInAnyOrder(NotificationType.APPOINTMENT_CONFIRMED,
                                NotificationType.APPOINTMENT_CANCELLED));

        mvc.perform(get("/notifications").with(patient(patientId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.type == 'APPOINTMENT_CONFIRMED')].body")
                        .value("Bonjour, votre rendez-vous du lundi 1 mars 2027 à 09h00 est confirmé. "
                                + "Pensez à vous munir de votre carte Vitale."))
                .andExpect(jsonPath("$[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$[0].status").value("SENT"));
        mvc.perform(get("/notifications").with(patient(UUID.randomUUID())).param("patientId", patientId.toString()))
                .andExpect(status().isForbidden());

        // Practitioners only see notifications of their own appointments; latest() is admin-only.
        mvc.perform(get("/notifications").with(staff("practitioner", practitionerId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/notifications").with(staff("practitioner", UUID.randomUUID()))
                        .param("patientId", patientId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/notifications").with(staff("practitioner", null)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/notifications").with(staff("admin", null)).param("patientId", patientId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void malformedEventsEndUpInTheDeadLetterTopic() {
        String key = "poison-" + UUID.randomUUID();
        sendRaw("{this is not json", key);

        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(Topics.NOTIFICATION_DLT));
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        return true;
                    }
                }
                return false;
            });
        }
    }
}
