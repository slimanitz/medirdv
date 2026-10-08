package io.github.slimanitz.medirdv.platform.outbox;

import io.github.slimanitz.medirdv.events.AggregateTypes;
import io.github.slimanitz.medirdv.events.AppointmentRequested;
import io.github.slimanitz.medirdv.events.EventCodec;
import io.github.slimanitz.medirdv.events.EventEnvelope;
import io.github.slimanitz.medirdv.events.Topics;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.NetworkException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

    private final OutboxRepository repository = mock(OutboxRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final EventCodec codec = new EventCodec();
    private final OutboxPublisher publisher =
            new OutboxPublisher(repository, kafka, TransactionOperations.withoutTransaction(), codec, 50, 3);

    @BeforeEach
    void lockAvailable() {
        when(repository.tryAcquirePublisherLock()).thenReturn(true);
    }

    private OutboxMessage message(String traceparent, int attempts) {
        UUID appointmentId = UUID.randomUUID();
        EventEnvelope envelope = codec.wrap(new AppointmentRequested(appointmentId, UUID.randomUUID(),
                UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(1800)), traceparent);
        return new OutboxMessage(envelope.eventId(), AggregateTypes.APPOINTMENT, appointmentId.toString(),
                envelope.type(), codec.write(envelope), attempts);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesBatchKeyedByAggregateAndMarksItSent() {
        OutboxMessage first = message("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01", 0);
        OutboxMessage second = message(null, 0);
        when(repository.lockPending(50)).thenReturn(List.of(first, second));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        int sent = publisher.publishPending();

        assertThat(sent).isEqualTo(2);
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka, times(2)).send(captor.capture());
        ProducerRecord<String, String> record = captor.getAllValues().getFirst();
        assertThat(record.topic()).isEqualTo(Topics.APPOINTMENT_EVENTS);
        assertThat(record.key()).isEqualTo(first.aggregateId());
        assertThat(new String(record.headers().lastHeader("traceparent").value()))
                .isEqualTo("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
        assertThat(captor.getAllValues().get(1).headers().lastHeader("traceparent")).isNull();
        verify(repository).markSent(List.of(first.id(), second.id()));
    }

    @Test
    void doesNothingWhenOutboxIsEmptyOrAnotherReplicaPublishes() {
        when(repository.lockPending(50)).thenReturn(List.of());
        assertThat(publisher.publishPending()).isZero();

        when(repository.tryAcquirePublisherLock()).thenReturn(false);
        assertThat(publisher.publishPending()).isZero();

        verify(repository, times(1)).lockPending(50);
        verify(repository, never()).markSent(anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void transientKafkaErrorsStopTheBatchButNeverParkTheMessage() {
        OutboxMessage stuck = message(null, 10);
        OutboxMessage next = message(null, 0);
        when(repository.lockPending(50)).thenReturn(List.of(stuck, next));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new NetworkException("broker down")));

        assertThat(publisher.publishPending()).isZero();

        verify(repository).recordFailure(eq(stuck.id()), contains("broker down"), eq(false));
        verify(kafka, times(1)).send(any(ProducerRecord.class)); // "next" must not overtake "stuck"
        verify(repository, never()).markSent(anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void poisonMessageIsParkedAfterMaxAttemptsAndUnblocksTheQueue() {
        OutboxMessage poison = new OutboxMessage(UUID.randomUUID(), AggregateTypes.APPOINTMENT, "a1",
                "AppointmentRequested", "{not an envelope", 2);
        OutboxMessage tooLarge = message(null, 0);
        when(repository.lockPending(50)).thenReturn(List.of(poison));

        publisher.publishPending();

        verify(repository).recordFailure(eq(poison.id()), anyString(), eq(true));
        verify(kafka, never()).send(any(ProducerRecord.class));

        when(repository.lockPending(50)).thenReturn(List.of(tooLarge));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RecordTooLargeException("too big")));
        publisher.publishPending();
        verify(repository).recordFailure(eq(tooLarge.id()), contains("too big"), eq(false));
    }

    @Test
    void classifiesKafkaErrors() {
        assertThat(OutboxPublisher.isTransient(new RuntimeException(new NetworkException("x")))).isTrue();
        assertThat(OutboxPublisher.isTransient(new java.util.concurrent.TimeoutException())).isTrue();
        assertThat(OutboxPublisher.isTransient(new RecordTooLargeException("x"))).isFalse();
        assertThat(OutboxPublisher.isTransient(new EventCodec.EventSerializationException("x", null))).isFalse();
        verify(repository, never()).recordFailure(any(), anyString(), anyBoolean());
    }
}
