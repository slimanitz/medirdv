package io.github.slimanitz.medirdv.events;

import java.time.Instant;
import java.util.UUID;

/** Published when an appointment is cancelled by a patient, a practitioner, an admin or the system. */
public record AppointmentCancelled(UUID appointmentId, UUID patientId, UUID practitionerId, Instant slotStart,
                                   CancelledBy cancelledBy, CancellationReason reasonCode) implements DomainEvent {

    @Override
    public String aggregateType() {
        return AggregateTypes.APPOINTMENT;
    }

    @Override
    public String key() {
        return appointmentId.toString();
    }
}
