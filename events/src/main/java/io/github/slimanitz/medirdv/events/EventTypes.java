package io.github.slimanitz.medirdv.events;

/** Event type names written in {@link EventEnvelope#type()}. */
public final class EventTypes {

    public static final String APPOINTMENT_REQUESTED = "AppointmentRequested";
    public static final String APPOINTMENT_CONFIRMED = "AppointmentConfirmed";
    public static final String APPOINTMENT_REJECTED = "AppointmentRejected";
    public static final String APPOINTMENT_CANCELLED = "AppointmentCancelled";
    public static final String APPOINTMENT_RESCHEDULED = "AppointmentRescheduled";
    public static final String PATIENT_VALIDATED = "PatientValidated";
    public static final String PATIENT_REJECTED = "PatientRejected";
    public static final String PATIENT_DEACTIVATED = "PatientDeactivated";

    private EventTypes() {
    }
}
