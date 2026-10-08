package io.github.slimanitz.medirdv.notification;

import io.github.slimanitz.medirdv.events.AppointmentCancelled;
import io.github.slimanitz.medirdv.events.AppointmentConfirmed;
import io.github.slimanitz.medirdv.events.AppointmentRejected;
import io.github.slimanitz.medirdv.events.AppointmentRescheduled;
import io.github.slimanitz.medirdv.events.EventCodec;
import io.github.slimanitz.medirdv.events.EventEnvelope;
import io.github.slimanitz.medirdv.events.EventTypes;
import io.github.slimanitz.medirdv.platform.idempotency.ProcessedEvents;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final ProcessedEvents processedEvents;
    private final NotificationSender sender;
    private final EventCodec codec;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, ProcessedEvents processedEvents,
                               NotificationSender sender, EventCodec codec, Clock clock) {
        this.notifications = notifications;
        this.processedEvents = processedEvents;
        this.sender = sender;
        this.codec = codec;
        this.clock = clock;
    }

    /** Builds, stores and sends the notification for an appointment event. Idempotent. */
    @Transactional
    public Optional<Notification> handle(EventEnvelope envelope) {
        Optional<Notification> notification = build(envelope);
        if (notification.isEmpty() || !processedEvents.markProcessed(envelope.eventId())) {
            return Optional.empty();
        }
        Notification saved = notifications.save(notification.get());
        sender.send(saved);
        return Optional.of(saved);
    }

    private Optional<Notification> build(EventEnvelope envelope) {
        return switch (envelope.type()) {
            case EventTypes.APPOINTMENT_CONFIRMED -> {
                var e = codec.payload(envelope, AppointmentConfirmed.class);
                yield Optional.of(create(envelope, e.patientId(), e.appointmentId(), e.practitionerId(),
                        NotificationType.APPOINTMENT_CONFIRMED, NotificationTemplates.confirmed(e)));
            }
            case EventTypes.APPOINTMENT_REJECTED -> {
                var e = codec.payload(envelope, AppointmentRejected.class);
                yield Optional.of(create(envelope, e.patientId(), e.appointmentId(), e.practitionerId(),
                        NotificationType.APPOINTMENT_REJECTED, NotificationTemplates.rejected(e)));
            }
            case EventTypes.APPOINTMENT_CANCELLED -> {
                var e = codec.payload(envelope, AppointmentCancelled.class);
                yield Optional.of(create(envelope, e.patientId(), e.appointmentId(), e.practitionerId(),
                        NotificationType.APPOINTMENT_CANCELLED, NotificationTemplates.cancelled(e)));
            }
            case EventTypes.APPOINTMENT_RESCHEDULED -> {
                var e = codec.payload(envelope, AppointmentRescheduled.class);
                yield Optional.of(create(envelope, e.patientId(), e.appointmentId(), e.practitionerId(),
                        NotificationType.APPOINTMENT_RESCHEDULED, NotificationTemplates.rescheduled(e)));
            }
            default -> Optional.empty();
        };
    }

    private Notification create(EventEnvelope envelope, UUID patientId, UUID appointmentId, UUID practitionerId,
                                NotificationType type, String body) {
        return new Notification(envelope.eventId(), patientId, appointmentId, practitionerId, type, body,
                clock.instant());
    }

    @Transactional(readOnly = true)
    public List<Notification> forPatient(UUID patientId) {
        return notifications.findTop100ByPatientIdOrderByCreatedAtDesc(patientId);
    }

    @Transactional(readOnly = true)
    public List<Notification> forPractitioner(UUID practitionerId) {
        return notifications.findTop100ByPractitionerIdOrderByCreatedAtDesc(practitionerId);
    }

    @Transactional(readOnly = true)
    public List<Notification> forPatientAndPractitioner(UUID patientId, UUID practitionerId) {
        return notifications.findTop100ByPatientIdAndPractitionerIdOrderByCreatedAtDesc(patientId, practitionerId);
    }

    @Transactional(readOnly = true)
    public List<Notification> latest() {
        return notifications.findTop100ByOrderByCreatedAtDesc();
    }
}
