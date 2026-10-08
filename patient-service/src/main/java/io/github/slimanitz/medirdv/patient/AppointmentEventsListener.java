package io.github.slimanitz.medirdv.patient;

import io.github.slimanitz.medirdv.events.AppointmentRequested;
import io.github.slimanitz.medirdv.events.EventCodec;
import io.github.slimanitz.medirdv.events.EventEnvelope;
import io.github.slimanitz.medirdv.events.EventTypes;
import io.github.slimanitz.medirdv.events.Topics;
import io.github.slimanitz.medirdv.platform.idempotency.ProcessedEvents;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Saga step 2: answers AppointmentRequested with PatientValidated or PatientRejected. */
@Component
public class AppointmentEventsListener {

    private final EventCodec codec;
    private final ProcessedEvents processedEvents;
    private final PatientService service;

    public AppointmentEventsListener(EventCodec codec, ProcessedEvents processedEvents, PatientService service) {
        this.codec = codec;
        this.processedEvents = processedEvents;
        this.service = service;
    }

    @KafkaListener(topics = Topics.APPOINTMENT_EVENTS, groupId = "patient-service")
    @Transactional
    public void onAppointmentEvent(ConsumerRecord<String, String> record) {
        EventEnvelope envelope = codec.read(record.value());
        if (!EventTypes.APPOINTMENT_REQUESTED.equals(envelope.type())
                || !processedEvents.markProcessed(envelope.eventId())) {
            return;
        }
        service.validateBooking(codec.payload(envelope, AppointmentRequested.class));
    }
}
