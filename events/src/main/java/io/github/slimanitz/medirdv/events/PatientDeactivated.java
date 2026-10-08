package io.github.slimanitz.medirdv.events;

import java.util.UUID;

/** Published when a patient is deactivated; scheduling cancels the future appointments. */
public record PatientDeactivated(UUID patientId) implements DomainEvent {

    @Override
    public String aggregateType() {
        return AggregateTypes.PATIENT;
    }

    @Override
    public String key() {
        return patientId.toString();
    }
}
