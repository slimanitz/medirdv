package io.github.slimanitz.medirdv.events;

/** Kafka topic names shared by every service. */
public final class Topics {

    public static final String PATIENT_EVENTS = "patient.events";
    public static final String APPOINTMENT_EVENTS = "appointment.events";
    public static final String NOTIFICATION_DLT = "notification.dlt";
    /** Dead letter topic of scheduling-service's patient.events consumer. */
    public static final String PATIENT_EVENTS_DLT = PATIENT_EVENTS + ".dlt";
    /** Dead letter topic of patient-service's appointment.events consumer. */
    public static final String APPOINTMENT_EVENTS_DLT = APPOINTMENT_EVENTS + ".dlt";

    private Topics() {
    }

    /** Dead letter topic of a consumed topic ({@code <topic>.dlt}). */
    public static String deadLetter(String topic) {
        return topic + ".dlt";
    }

    /** Resolves the topic an aggregate publishes to (used by the outbox publisher). */
    public static String forAggregate(String aggregateType) {
        return switch (aggregateType) {
            case AggregateTypes.PATIENT -> PATIENT_EVENTS;
            case AggregateTypes.APPOINTMENT -> APPOINTMENT_EVENTS;
            default -> throw new IllegalArgumentException("Unknown aggregate type: " + aggregateType);
        };
    }
}
