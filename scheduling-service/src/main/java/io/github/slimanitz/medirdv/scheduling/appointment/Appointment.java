package io.github.slimanitz.medirdv.scheduling.appointment;

import io.github.slimanitz.medirdv.events.CancellationReason;
import io.github.slimanitz.medirdv.events.RejectionReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Appointment aggregate. Holds the saga state machine:
 * <pre>
 * PENDING_VALIDATION --confirm--> CONFIRMED --reschedule--> CONFIRMED
 *        |   \--reject--> REJECTED                  |
 *        \------------cancel------> CANCELLED <-----/
 * </pre>
 */
@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;

    @Column(name = "start_at", nullable = false)
    private Instant start;

    @Column(name = "end_at", nullable = false)
    private Instant end;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    /** Reason for the visit, typed by the patient. Never leaves this service. */
    private String reason;

    /** Coded reason of a rejection or cancellation (enum name). */
    @Column(name = "status_reason")
    private String statusReason;

    /** Optional free-text comment of a cancellation. Never leaves this service. */
    @Column(name = "status_comment")
    private String statusComment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Appointment() {
    }

    private Appointment(UUID id, UUID patientId, UUID practitionerId, Instant start, Instant end, String reason,
                        Instant now) {
        this.id = id;
        this.patientId = patientId;
        this.practitionerId = practitionerId;
        this.start = start;
        this.end = end;
        this.reason = reason;
        this.status = AppointmentStatus.PENDING_VALIDATION;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Saga step 1: the slot is reserved while the patient is validated. */
    public static Appointment request(UUID patientId, UUID practitionerId, Instant start, Instant end, String reason,
                                      Instant now) {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("La fin du créneau doit être après le début");
        }
        return new Appointment(UUID.randomUUID(), patientId, practitionerId, start, end, reason, now);
    }

    public void confirm(Instant now) {
        requireStatus(AppointmentStatus.PENDING_VALIDATION, "confirmer");
        this.status = AppointmentStatus.CONFIRMED;
        this.updatedAt = now;
    }

    /** Compensation: releases the slot. */
    public void reject(RejectionReason reason, Instant now) {
        requireStatus(AppointmentStatus.PENDING_VALIDATION, "refuser");
        this.status = AppointmentStatus.REJECTED;
        this.statusReason = reason.name();
        this.updatedAt = now;
    }

    public void cancel(CancellationReason reason, String comment, Instant now) {
        if (!isActive()) {
            throw new IllegalStateException("Impossible d'annuler un rendez-vous au statut " + status);
        }
        this.status = AppointmentStatus.CANCELLED;
        this.statusReason = reason.name();
        this.statusComment = comment == null || comment.isBlank() ? null : comment.strip();
        this.updatedAt = now;
    }

    public void reschedule(Instant newStart, Instant newEnd, Instant now) {
        requireStatus(AppointmentStatus.CONFIRMED, "déplacer");
        if (!newEnd.isAfter(newStart)) {
            throw new IllegalArgumentException("La fin du créneau doit être après le début");
        }
        this.start = newStart;
        this.end = newEnd;
        this.updatedAt = now;
    }

    public boolean isActive() {
        return AppointmentStatus.ACTIVE.contains(status);
    }

    public boolean isPending() {
        return status == AppointmentStatus.PENDING_VALIDATION;
    }

    private void requireStatus(AppointmentStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException("Impossible de " + action + " un rendez-vous au statut " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getPractitionerId() {
        return practitionerId;
    }

    public Instant getStart() {
        return start;
    }

    public Instant getEnd() {
        return end;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public String getStatusComment() {
        return statusComment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
