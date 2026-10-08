package io.github.slimanitz.medirdv.platform.kafka;

import io.github.slimanitz.medirdv.events.EventCodec;
import io.github.slimanitz.medirdv.events.Topics;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Blocking retries for the saga consumers (scheduling, patient): ordering per aggregate matters
 * there, so a failing record is retried in place with exponential backoff, then published to
 * {@code <topic>.dlt} and the partition moves on. Unreadable messages go to the DLT at once.
 * Spring Boot applies this handler to the default listener container factory.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaErrorHandlingConfiguration {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafkaOperations,
                                          @Value("${medirdv.kafka.retry.max-retries:4}") int maxRetries,
                                          @Value("${medirdv.kafka.retry.initial-interval-ms:500}") long initialInterval) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, exception) -> new TopicPartition(Topics.deadLetter(record.topic()), -1));
        var backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialInterval);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000);
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(EventCodec.EventSerializationException.class);
        return handler;
    }
}
