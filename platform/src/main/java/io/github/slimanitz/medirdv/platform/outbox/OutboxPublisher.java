package io.github.slimanitz.medirdv.platform.outbox;

import io.github.slimanitz.medirdv.events.EventCodec;
import io.github.slimanitz.medirdv.events.Topics;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.RetriableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polling publisher: every {@code medirdv.outbox.poll-interval-ms} it takes the publisher lock,
 * locks a batch of pending messages, publishes them in order (acks=all, idempotent producer) and
 * marks them sent in the same database transaction. Delivery is at-least-once; consumers
 * deduplicate on the event id.
 *
 * <p>Failures: the batch stops at the first failing message (later messages may belong to the
 * same aggregate and must not overtake it). Transient Kafka errors (broker down, timeouts) are
 * retried forever. Permanent errors (unreadable payload, record too large, unknown topic) are
 * retried {@code maxAttempts} times, then the message is parked with status {@code FAILED} so that
 * it no longer blocks the queue ({@code medirdv_outbox_failed} gauge).
 */
public class OutboxPublisher {

    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String EVENT_TYPE_HEADER = "eventType";

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionOperations transactions;
    private final EventCodec codec;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                           TransactionOperations transactions, EventCodec codec, int batchSize, int maxAttempts) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactions = transactions;
        this.codec = codec;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${medirdv.outbox.poll-interval-ms:500}")
    public void scheduledPublish() {
        try {
            publishPending();
        } catch (RuntimeException e) {
            log.warn("Outbox publication failed, will retry on next tick: {}", e.getMessage());
        }
    }

    /** Publishes one batch; returns the number of messages sent. */
    public int publishPending() {
        Integer sent = transactions.execute(status -> {
            if (!repository.tryAcquirePublisherLock()) {
                return 0; // another replica is publishing
            }
            List<OutboxMessage> batch = repository.lockPending(batchSize);
            List<UUID> ids = new ArrayList<>(batch.size());
            for (OutboxMessage message : batch) {
                try {
                    send(message);
                    ids.add(message.id());
                } catch (RuntimeException e) {
                    handleFailure(message, e);
                    break;
                }
            }
            if (!ids.isEmpty()) {
                repository.markSent(ids);
                log.debug("Published {} outbox messages", ids.size());
            }
            return ids.size();
        });
        return sent == null ? 0 : sent;
    }

    private void handleFailure(OutboxMessage message, RuntimeException e) {
        boolean transientError = isTransient(e);
        boolean park = !transientError && message.attempts() + 1 >= maxAttempts;
        repository.recordFailure(message.id(), rootMessage(e), park);
        if (park) {
            log.error("Outbox message {} ({}) parked as FAILED after {} attempts: {}", message.id(), message.type(),
                    message.attempts() + 1, rootMessage(e));
        } else {
            log.warn("Outbox message {} ({}) not published ({} error): {}", message.id(), message.type(),
                    transientError ? "transient" : "permanent", rootMessage(e));
        }
    }

    static boolean isTransient(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof RetriableException || t instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    private void send(OutboxMessage message) {
        var record = new ProducerRecord<String, String>(Topics.forAggregate(message.aggregateType()),
                message.aggregateId(), message.payload());
        record.headers().add(EVENT_TYPE_HEADER, message.type().getBytes(StandardCharsets.UTF_8));
        String traceparent = codec.read(message.payload()).traceparent();
        if (traceparent != null) {
            record.headers().add(TRACEPARENT_HEADER, traceparent.getBytes(StandardCharsets.UTF_8));
        }
        try {
            kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing " + message.id(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new OutboxPublicationException(message.id(), e instanceof ExecutionException ? e.getCause() : e);
        }
    }

    /** Wraps the Kafka failure of one message. */
    public static class OutboxPublicationException extends RuntimeException {
        OutboxPublicationException(UUID id, Throwable cause) {
            super("Kafka publication failed for outbox message " + id, cause);
        }
    }
}
