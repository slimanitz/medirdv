package io.github.slimanitz.medirdv.notification;

import io.github.slimanitz.medirdv.events.AppointmentCancelled;
import io.github.slimanitz.medirdv.events.AppointmentConfirmed;
import io.github.slimanitz.medirdv.events.AppointmentRejected;
import io.github.slimanitz.medirdv.events.AppointmentRescheduled;
import io.github.slimanitz.medirdv.events.CancellationReason;
import io.github.slimanitz.medirdv.events.CancelledBy;
import io.github.slimanitz.medirdv.events.RejectionReason;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplatesTest {

    private final UUID id = UUID.randomUUID();
    private final Instant start = Instant.parse("2026-10-12T07:00:00Z");

    @Test
    void rendersConfirmationInFrenchAndParisTime() {
        String body = NotificationTemplates.confirmed(
                new AppointmentConfirmed(id, id, id, start, start.plusSeconds(1800)));

        assertThat(body).isEqualTo("Bonjour, votre rendez-vous du lundi 12 octobre 2026 à 09h00 est confirmé. "
                + "Pensez à vous munir de votre carte Vitale.");
    }

    @Test
    void explainsRejectionAndCancellation() {
        assertThat(NotificationTemplates.rejected(new AppointmentRejected(id, id, id, start, RejectionReason.VALIDATION_TIMEOUT)))
                .contains("la validation de votre dossier a expiré").contains("Le créneau a été libéré");
        assertThat(NotificationTemplates.cancelled(
                new AppointmentCancelled(id, id, id, start, CancelledBy.PRACTITIONER,
                        CancellationReason.PRACTITIONER_UNAVAILABLE)))
                .isEqualTo("Bonjour, votre rendez-vous du lundi 12 octobre 2026 à 09h00 a été annulé "
                        + "par votre praticien (indisponibilité du praticien).");
        assertThat(NotificationTemplates.cancelled(
                new AppointmentCancelled(id, id, id, start, CancelledBy.PATIENT, CancellationReason.PATIENT_REQUEST)))
                .endsWith("a été annulé à votre demande.");
    }

    @Test
    void rendersRescheduling() {
        Instant winter = Instant.parse("2026-11-02T13:30:00Z");

        assertThat(NotificationTemplates.rescheduled(
                new AppointmentRescheduled(id, id, id, start, winter, winter.plusSeconds(1800))))
                .endsWith("a été déplacé au lundi 2 novembre 2026 à 14h30.");
    }
}
