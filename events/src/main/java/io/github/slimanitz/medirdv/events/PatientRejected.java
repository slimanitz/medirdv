package io.github.slimanitz.medirdv.events;

import java.util.UUID;

/** Published by patient-service: unknown or inactive patient (saga step 2). */
public record PatientRejected(UUID appointmentId, UUID patientId, RejectionReason reason) implements DomainEvent {

    @Override
    public String aggregateType() {
        return AggregateTypes.PATIENT;
    }

    @Override
    public String key() {
        return appointmentId.toString();
    }
}
