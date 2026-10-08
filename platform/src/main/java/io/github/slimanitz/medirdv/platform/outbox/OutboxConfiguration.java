package io.github.slimanitz.medirdv.platform.outbox;

import io.github.slimanitz.medirdv.events.EventCodec;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Wires the transactional outbox for services that produce events. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class OutboxConfiguration {

    @Bean
    OutboxRepository outboxRepository(JdbcTemplate jdbcTemplate) {
        return new OutboxRepository(jdbcTemplate);
    }

    @Bean
    OutboxWriter outboxWriter(OutboxRepository repository, EventCodec codec, ObjectProvider<Tracer> tracer) {
        return new OutboxWriter(repository, codec, tracer.getIfAvailable(() -> Tracer.NOOP));
    }

    @Bean
    OutboxPublisher outboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                                    PlatformTransactionManager transactionManager, EventCodec codec,
                                    @Value("${medirdv.outbox.batch-size:50}") int batchSize,
                                    @Value("${medirdv.outbox.max-attempts:5}") int maxAttempts) {
        return new OutboxPublisher(repository, kafkaTemplate, new TransactionTemplate(transactionManager), codec,
                batchSize, maxAttempts);
    }

    /** Number of events waiting in the outbox, exposed as {@code medirdv_outbox_backlog}. */
    @Bean
    Gauge outboxBacklogGauge(MeterRegistry registry, OutboxRepository repository) {
        return Gauge.builder("medirdv.outbox.backlog", repository, r -> safe(r::countPending))
                .description("Events written to the outbox and not yet published to Kafka")
                .register(registry);
    }

    /** Poison messages parked after too many attempts, exposed as {@code medirdv_outbox_failed}. */
    @Bean
    Gauge outboxFailedGauge(MeterRegistry registry, OutboxRepository repository) {
        return Gauge.builder("medirdv.outbox.failed", repository, r -> safe(r::countFailed))
                .description("Outbox messages parked with status FAILED (need an operator)")
                .register(registry);
    }

    private static double safe(java.util.function.LongSupplier count) {
        try {
            return count.getAsLong();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}
