package io.github.slimanitz.medirdv.patient;

import io.github.slimanitz.medirdv.events.AppointmentRequested;
import io.github.slimanitz.medirdv.events.PatientDeactivated;
import io.github.slimanitz.medirdv.events.PatientRejected;
import io.github.slimanitz.medirdv.events.PatientValidated;
import io.github.slimanitz.medirdv.events.RejectionReason;
import io.github.slimanitz.medirdv.platform.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);

    private final PatientRepository patients;
    private final OutboxWriter outbox;
    private final Clock clock;

    public PatientService(PatientRepository patients, OutboxWriter outbox, Clock clock) {
        this.patients = patients;
        this.outbox = outbox;
        this.clock = clock;
    }

    public record PatientData(String firstName, String lastName, String email, String phone, LocalDate birthDate) {
    }

    @Transactional(readOnly = true)
    public List<Patient> list() {
        return patients.findAllByOrderByLastNameAscFirstNameAsc();
    }

    @Transactional(readOnly = true)
    public Patient get(UUID id) {
        return patients.findById(id).orElseThrow(() -> new PatientNotFoundException(id));
    }

    @Transactional
    public Patient create(PatientData data) {
        if (patients.existsByEmailIgnoreCase(data.email().strip())) {
            throw new DuplicateEmailException();
        }
        Patient patient = new Patient(UUID.randomUUID(), data.firstName(), data.lastName(), data.email(),
                data.phone(), data.birthDate(), clock.instant());
        patients.save(patient);
        log.info("Created {}", patient);
        return patient;
    }

    @Transactional
    public Patient update(UUID id, PatientData data) {
        Patient patient = get(id);
        if (patients.existsByEmailIgnoreCaseAndIdNot(data.email().strip(), id)) {
            throw new DuplicateEmailException();
        }
        patient.update(data.firstName(), data.lastName(), data.email(), data.phone(), data.birthDate(),
                clock.instant());
        log.info("Updated {}", patient);
        return patient;
    }

    /** Deactivation is published so that scheduling cancels the patient's future appointments. */
    @Transactional
    public Patient deactivate(UUID id) {
        Patient patient = get(id);
        if (patient.deactivate(clock.instant())) {
            outbox.record(new PatientDeactivated(id));
            log.info("Deactivated {}", patient);
        }
        return patient;
    }

    /** Saga step 2: validate the patient behind a booking request. */
    @Transactional
    public void validateBooking(AppointmentRequested request) {
        Optional<Patient> patient = patients.findById(request.patientId());
        if (patient.isPresent() && patient.get().isActive()) {
            outbox.record(new PatientValidated(request.appointmentId(), request.patientId()));
            log.info("Patient {} validated for appointment {}", request.patientId(), request.appointmentId());
        } else {
            RejectionReason reason = patient.isEmpty() ? RejectionReason.UNKNOWN_PATIENT
                    : RejectionReason.PATIENT_INACTIVE;
            outbox.record(new PatientRejected(request.appointmentId(), request.patientId(), reason));
            log.info("Patient {} rejected for appointment {}: {}", request.patientId(), request.appointmentId(),
                    reason);
        }
    }

    public static class PatientNotFoundException extends RuntimeException {
        public PatientNotFoundException(UUID id) {
            super("Patient introuvable : " + id);
        }
    }

    public static class DuplicateEmailException extends RuntimeException {
        public DuplicateEmailException() {
            super("Un patient avec cette adresse e-mail existe déjà");
        }
    }
}
