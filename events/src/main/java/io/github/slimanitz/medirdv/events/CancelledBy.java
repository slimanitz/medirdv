package io.github.slimanitz.medirdv.events;

/** Who cancelled an appointment: a role, never a person. */
public enum CancelledBy {
    PATIENT,
    PRACTITIONER,
    ADMIN,
    SYSTEM
}
