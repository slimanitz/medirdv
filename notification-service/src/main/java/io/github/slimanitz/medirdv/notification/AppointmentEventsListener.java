package io.github.slimanitz.medirdv.notification;

import io.github.slimanitz.medirdv.events.EventCodec;
import io.github.slimanitz.medirdv.events.Topics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Non-blocking retries: a failing event goes to {@code appointment.events-retry-N} topics
 * (3 attempts in total, exponential backoff) then to the dead letter topic {@code notification.dlt}.
 * Malformed messages skip the retries.
 *
 * <p>Trade-off (ADR 0004): a retried event is processed after later events of the same
 * appointment, e.g. a cancellation notice may be created before a delayed confirmation notice.
 * Each notification is a self-contained message, so this is accepted in exchange for never
 * blocking the partition.
 */
@Component
public class AppointmentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEventsListener.class);

    private final NotificationService service;
    private final EventCodec codec;
    private final Counter deadLetters;

    public AppointmentEventsListener(NotificationService service, EventCodec codec, MeterRegistry registry) {
        this.service = service;
        this.codec = codec;
        this.deadLetters = Counter.builder("medirdv.notification.dead.letters")
                .description("Events moved to the notification dead letter topic").register(registry);
    }

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2.0),
            exclude = EventCodec.EventSerializationException.class,
            numPartitions = "${medirdv.kafka.partitions:3}", replicationFactor = "${medirdv.kafka.replicas:1}")
    @KafkaListener(topics = Topics.APPOINTMENT_EVENTS, groupId = "notification-service")
    public void onAppointmentEvent(ConsumerRecord<String, String> record) {
        service.handle(codec.read(record.value()));
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> record,
                             @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) byte[] error) {
        deadLetters.increment();
        log.error("Event moved to {} (key={}): {}", record.topic(), record.key(),
                error == null ? "unknown error" : new String(error, java.nio.charset.StandardCharsets.UTF_8));
    }
}
