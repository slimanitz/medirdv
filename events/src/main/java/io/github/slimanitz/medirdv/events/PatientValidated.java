package io.github.slimanitz.medirdv.events;

import java.util.UUID;

/** Published by patient-service: the patient exists and is active (saga step 2). */
public record PatientValidated(UUID appointmentId, UUID patientId) implements DomainEvent {

    @Override
    public String aggregateType() {
        return AggregateTypes.PATIENT;
    }

    @Override
    public String key() {
        return appointmentId.toString();
    }
}
