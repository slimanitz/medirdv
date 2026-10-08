package io.github.slimanitz.medirdv.platform.kafka;

import io.github.slimanitz.medirdv.events.EventCodec;
import io.github.slimanitz.medirdv.events.Topics;
import io.github.slimanitz.medirdv.platform.idempotency.ProcessedEvents;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Topics, event codec and idempotency store shared by every service. Partitions and
 * replication factor come from {@code medirdv.kafka.partitions} / {@code medirdv.kafka.replicas}
 * (env {@code MEDIRDV_KAFKA_PARTITIONS} / {@code MEDIRDV_KAFKA_REPLICAS}); use 3 replicas on a
 * production cluster.
 */
@Configuration(proxyBeanMethods = false)
public class MessagingConfiguration {

    @Bean
    EventCodec eventCodec() {
        return new EventCodec();
    }

    @Bean
    ProcessedEvents processedEvents(JdbcTemplate jdbcTemplate) {
        return new ProcessedEvents(jdbcTemplate);
    }

    @Bean
    KafkaAdmin.NewTopics medirdvTopics(@Value("${medirdv.kafka.partitions:3}") int partitions,
                                      @Value("${medirdv.kafka.replicas:1}") int replicas) {
        return new KafkaAdmin.NewTopics(
                topic(Topics.PATIENT_EVENTS, partitions, replicas),
                topic(Topics.APPOINTMENT_EVENTS, partitions, replicas),
                topic(Topics.NOTIFICATION_DLT, 1, replicas),
                topic(Topics.PATIENT_EVENTS_DLT, 1, replicas),
                topic(Topics.APPOINTMENT_EVENTS_DLT, 1, replicas));
    }

    private static NewTopic topic(String name, int partitions, int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
}
