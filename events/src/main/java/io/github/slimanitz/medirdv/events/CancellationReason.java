package io.github.slimanitz.medirdv.events;

/**
 * Coded cancellation reason. Free-text comments typed by users stay in the scheduling database
 * and never travel in events (they may contain health information).
 */
public enum CancellationReason {
    PATIENT_REQUEST,
    PRACTITIONER_UNAVAILABLE,
    PATIENT_DEACTIVATED,
    OTHER
}
