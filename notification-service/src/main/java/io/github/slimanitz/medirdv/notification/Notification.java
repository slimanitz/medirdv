package io.github.slimanitz.medirdv.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    public enum Channel { EMAIL }

    public enum Status { SENT, FAILED }

    @Id
    private UUID id;

    /** Source event: unique, a redelivered event never produces a second notification. */
    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(name = "practitioner_id")
    private UUID practitionerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(UUID eventId, UUID patientId, UUID appointmentId, UUID practitionerId,
                        NotificationType type, String body, Instant now) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.patientId = patientId;
        this.appointmentId = appointmentId;
        this.practitionerId = practitionerId;
        this.type = type;
        this.channel = Channel.EMAIL;
        this.status = Status.SENT;
        this.body = body;
        this.createdAt = now;
    }

    public void markFailed() {
        this.status = Status.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public UUID getPractitionerId() {
        return practitionerId;
    }

    public NotificationType getType() {
        return type;
    }

    public Channel getChannel() {
        return channel;
    }

    public Status getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
