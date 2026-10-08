package io.github.slimanitz.medirdv.scheduling.appointment;

import java.util.EnumSet;
import java.util.Set;

public enum AppointmentStatus {
    PENDING_VALIDATION,
    CONFIRMED,
    REJECTED,
    CANCELLED;

    /** Statuses that hold the slot (partial unique index in the database). */
    public static final Set<AppointmentStatus> ACTIVE = EnumSet.of(PENDING_VALIDATION, CONFIRMED);
}
