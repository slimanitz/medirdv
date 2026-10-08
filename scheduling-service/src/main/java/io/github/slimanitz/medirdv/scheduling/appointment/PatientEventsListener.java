package io.github.slimanitz.medirdv.scheduling.appointment;

import io.github.slimanitz.medirdv.events.EventCodec;
import io.github.slimanitz.medirdv.events.EventEnvelope;
import io.github.slimanitz.medirdv.events.EventTypes;
import io.github.slimanitz.medirdv.events.PatientDeactivated;
import io.github.slimanitz.medirdv.events.PatientRejected;
import io.github.slimanitz.medirdv.events.PatientValidated;
import io.github.slimanitz.medirdv.events.Topics;
import io.github.slimanitz.medirdv.platform.idempotency.ProcessedEvents;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Consumes patient-service replies (saga step 3) and patient deactivations. Idempotent. */
@Component
public class PatientEventsListener {

    private static final Logger log = LoggerFactory.getLogger(PatientEventsListener.class);

    private final EventCodec codec;
    private final ProcessedEvents processedEvents;
    private final AppointmentService service;

    public PatientEventsListener(EventCodec codec, ProcessedEvents processedEvents, AppointmentService service) {
        this.codec = codec;
        this.processedEvents = processedEvents;
        this.service = service;
    }

    @KafkaListener(topics = Topics.PATIENT_EVENTS, groupId = "scheduling-service")
    @Transactional
    public void onPatientEvent(ConsumerRecord<String, String> record) {
        EventEnvelope envelope = codec.read(record.value());
        if (!processedEvents.markProcessed(envelope.eventId())) {
            log.debug("Duplicate event {} ignored", envelope.eventId());
            return;
        }
        switch (envelope.type()) {
            case EventTypes.PATIENT_VALIDATED ->
                    service.onPatientValidated(codec.payload(envelope, PatientValidated.class).appointmentId());
            case EventTypes.PATIENT_REJECTED -> {
                PatientRejected rejected = codec.payload(envelope, PatientRejected.class);
                service.onPatientRejected(rejected.appointmentId(), rejected.reason());
            }
            case EventTypes.PATIENT_DEACTIVATED -> {
                PatientDeactivated deactivated = codec.payload(envelope, PatientDeactivated.class);
                int cancelled = service.cancelFutureAppointmentsOf(deactivated.patientId());
                log.info("Patient {} deactivated: {} future appointments cancelled", deactivated.patientId(),
                        cancelled);
            }
            default -> log.debug("Ignoring event type {}", envelope.type());
        }
    }
}
