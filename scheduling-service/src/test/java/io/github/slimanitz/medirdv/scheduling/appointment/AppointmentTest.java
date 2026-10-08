package io.github.slimanitz.medirdv.scheduling.appointment;

import io.github.slimanitz.medirdv.events.CancellationReason;
import io.github.slimanitz.medirdv.events.RejectionReason;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentTest {

    private final Instant now = Instant.parse("2026-10-08T10:00:00Z");
    private final Instant start = Instant.parse("2026-10-12T07:00:00Z");

    private Appointment pending() {
        return Appointment.request(UUID.randomUUID(), UUID.randomUUID(), start, start.plusSeconds(1800), "Toux", now);
    }

    @Test
    void newAppointmentIsPendingValidationAndHoldsTheSlot() {
        Appointment appointment = pending();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PENDING_VALIDATION);
        assertThat(appointment.isActive()).isTrue();
    }

    @Test
    void validatedPatientConfirmsTheAppointment() {
        Appointment appointment = pending();

        appointment.confirm(now.plusSeconds(1));

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(appointment.getUpdatedAt()).isEqualTo(now.plusSeconds(1));
    }

    @Test
    void rejectionIsTheCompensationAndReleasesTheSlot() {
        Appointment appointment = pending();

        appointment.reject(RejectionReason.PATIENT_INACTIVE, now);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.REJECTED);
        assertThat(appointment.getStatusReason()).isEqualTo("PATIENT_INACTIVE");
        assertThat(appointment.isActive()).isFalse();
    }

    @Test
    void cannotConfirmOrRejectTwice() {
        Appointment confirmed = pending();
        confirmed.confirm(now);
        Appointment rejected = pending();
        rejected.reject(RejectionReason.UNKNOWN_PATIENT, now);

        assertThatThrownBy(() -> confirmed.confirm(now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> confirmed.reject(RejectionReason.VALIDATION_TIMEOUT, now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> rejected.confirm(now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pendingAndConfirmedAppointmentsCanBeCancelledOnce() {
        Appointment confirmed = pending();
        confirmed.confirm(now);
        Appointment stillPending = pending();

        confirmed.cancel(CancellationReason.PRACTITIONER_UNAVAILABLE, "  Congrès médical ", now);
        stillPending.cancel(CancellationReason.PATIENT_REQUEST, " ", now);

        assertThat(confirmed.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(stillPending.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(confirmed.getStatusReason()).isEqualTo("PRACTITIONER_UNAVAILABLE");
        assertThat(confirmed.getStatusComment()).isEqualTo("Congrès médical");
        assertThat(stillPending.getStatusComment()).isNull();
        assertThatThrownBy(() -> confirmed.cancel(CancellationReason.OTHER, null, now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyConfirmedAppointmentsCanBeRescheduled() {
        Appointment appointment = pending();
        Instant newStart = start.plusSeconds(86400);

        assertThatThrownBy(() -> appointment.reschedule(newStart, newStart.plusSeconds(1800), now))
                .isInstanceOf(IllegalStateException.class);

        appointment.confirm(now);
        appointment.reschedule(newStart, newStart.plusSeconds(1800), now);
        assertThat(appointment.getStart()).isEqualTo(newStart);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void rejectsInvertedTimeRange() {
        assertThatThrownBy(() -> Appointment.request(UUID.randomUUID(), UUID.randomUUID(), start, start, null, now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
