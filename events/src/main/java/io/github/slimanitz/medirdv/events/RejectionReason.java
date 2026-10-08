package io.github.slimanitz.medirdv.events;

/** Why a booking could not be confirmed by the saga. */
public enum RejectionReason {
    UNKNOWN_PATIENT,
    PATIENT_INACTIVE,
    VALIDATION_TIMEOUT
}
