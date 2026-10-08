package io.github.slimanitz.medirdv.events;

import java.time.Instant;
import java.util.UUID;

/** Published when the saga is compensated (patient rejected or validation timeout). */
public record AppointmentRejected(UUID appointmentId, UUID patientId, UUID practitionerId, Instant slotStart,
                                  RejectionReason reason) implements DomainEvent {

    @Override
    public String aggregateType() {
        return AggregateTypes.APPOINTMENT;
    }

    @Override
    public String key() {
        return appointmentId.toString();
    }
}
