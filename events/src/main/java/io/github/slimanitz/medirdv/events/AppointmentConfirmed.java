package io.github.slimanitz.medirdv.events;

import java.time.Instant;
import java.util.UUID;

/** Published when the patient was validated and the appointment is confirmed. */
public record AppointmentConfirmed(UUID appointmentId, UUID patientId, UUID practitionerId, Instant slotStart, Instant slotEnd) implements DomainEvent {

    @Override
    public String aggregateType() {
        return AggregateTypes.APPOINTMENT;
    }

    @Override
    public String key() {
        return appointmentId.toString();
    }
}
