package io.github.slimanitz.medirdv.notification;

import io.github.slimanitz.medirdv.events.DomainEvent;
import io.github.slimanitz.medirdv.events.EventCodec;
import io.github.slimanitz.medirdv.events.EventEnvelope;
import io.github.slimanitz.medirdv.events.Topics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Plain Kafka clients used by tests to play the role of the other services. */
public final class KafkaTestSupport {

    private static final EventCodec CODEC = new EventCodec();

    private KafkaTestSupport() {
    }

    public static EventEnvelope publish(String bootstrapServers, DomainEvent event) {
        EventEnvelope envelope = CODEC.wrap(event, null);
        try (var producer = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(new ProducerRecord<>(Topics.forAggregate(event.aggregateType()), event.key(),
                    CODEC.write(envelope))).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return envelope;
    }

    /** Reads the topic from the beginning until an envelope matches, or fails after the timeout. */
    public static EventEnvelope awaitEvent(String bootstrapServers, String topic, Predicate<EventEnvelope> match) {
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plusSeconds(30);
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    EventEnvelope envelope = CODEC.read(record.value());
                    if (match.test(envelope)) {
                        return envelope;
                    }
                }
            }
        }
        throw new AssertionError("No matching event received on " + topic);
    }

    public static <T extends DomainEvent> T payload(EventEnvelope envelope, Class<T> type) {
        return CODEC.payload(envelope, type);
    }
}
