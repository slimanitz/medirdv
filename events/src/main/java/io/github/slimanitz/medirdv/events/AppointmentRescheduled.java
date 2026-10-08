package io.github.slimanitz.medirdv.events;

import java.time.Instant;
import java.util.UUID;

/** Published when a confirmed appointment is moved to another free slot. */
public record AppointmentRescheduled(UUID appointmentId, UUID patientId, UUID practitionerId, Instant previousStart, Instant slotStart, Instant slotEnd) implements DomainEvent {

    @Override
    public String aggregateType() {
        return AggregateTypes.APPOINTMENT;
    }

    @Override
    public String key() {
        return appointmentId.toString();
    }
}
