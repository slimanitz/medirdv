package io.github.slimanitz.medirdv.events;

import java.time.Instant;
import java.util.UUID;

/** Published by scheduling-service when a booking is requested (saga step 1). */
public record AppointmentRequested(UUID appointmentId, UUID patientId, UUID practitionerId, Instant slotStart, Instant slotEnd) implements DomainEvent {

    @Override
    public String aggregateType() {
        return AggregateTypes.APPOINTMENT;
    }

    @Override
    public String key() {
        return appointmentId.toString();
    }
}
