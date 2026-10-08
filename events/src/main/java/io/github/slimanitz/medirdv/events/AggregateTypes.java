package io.github.slimanitz.medirdv.events;

/** Aggregate types stored in the outbox table. */
public final class AggregateTypes {

    public static final String PATIENT = "Patient";
    public static final String APPOINTMENT = "Appointment";

    private AggregateTypes() {
    }
}
