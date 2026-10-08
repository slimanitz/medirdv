package io.github.slimanitz.medirdv.notification;

import io.github.slimanitz.medirdv.events.AppointmentCancelled;
import io.github.slimanitz.medirdv.events.AppointmentConfirmed;
import io.github.slimanitz.medirdv.events.AppointmentRejected;
import io.github.slimanitz.medirdv.events.AppointmentRescheduled;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Renders French notification bodies. Only dates are available: events carry no personal data. */
public final class NotificationTemplates {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH'h'mm", Locale.FRANCE).withZone(PARIS);

    private NotificationTemplates() {
    }

    static String format(Instant instant) {
        return FORMAT.format(instant);
    }

    public static String confirmed(AppointmentConfirmed event) {
        return "Bonjour, votre rendez-vous du " + format(event.slotStart()) + " est confirmé. "
                + "Pensez à vous munir de votre carte Vitale.";
    }

    public static String rejected(AppointmentRejected event) {
        String why = switch (event.reason()) {
            case PATIENT_INACTIVE -> "votre dossier patient est désactivé";
            case UNKNOWN_PATIENT -> "votre dossier patient est introuvable";
            case VALIDATION_TIMEOUT -> "la validation de votre dossier a expiré";
        };
        return "Bonjour, votre demande de rendez-vous du " + format(event.slotStart())
                + " n'a pas pu être confirmée : " + why + ". Le créneau a été libéré.";
    }

    public static String cancelled(AppointmentCancelled event) {
        String by = switch (event.cancelledBy()) {
            case PATIENT -> "à votre demande";
            case PRACTITIONER -> "par votre praticien";
            case ADMIN -> "par le secrétariat";
            case SYSTEM -> "automatiquement";
        };
        String why = switch (event.reasonCode()) {
            case PRACTITIONER_UNAVAILABLE -> " (indisponibilité du praticien)";
            case PATIENT_DEACTIVATED -> " (dossier patient désactivé)";
            case PATIENT_REQUEST, OTHER -> "";
        };
        return "Bonjour, votre rendez-vous du " + format(event.slotStart()) + " a été annulé " + by + why + ".";
    }

    public static String rescheduled(AppointmentRescheduled event) {
        return "Bonjour, votre rendez-vous du " + format(event.previousStart()) + " a été déplacé au "
                + format(event.slotStart()) + ".";
    }
}
