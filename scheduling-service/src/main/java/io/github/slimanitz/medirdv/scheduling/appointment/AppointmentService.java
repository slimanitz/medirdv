package io.github.slimanitz.medirdv.scheduling.appointment;

import io.github.slimanitz.medirdv.events.AppointmentCancelled;
import io.github.slimanitz.medirdv.events.CancellationReason;
import io.github.slimanitz.medirdv.events.CancelledBy;
import io.github.slimanitz.medirdv.events.RejectionReason;
import io.github.slimanitz.medirdv.events.AppointmentConfirmed;
import io.github.slimanitz.medirdv.events.AppointmentRejected;
import io.github.slimanitz.medirdv.events.AppointmentRequested;
import io.github.slimanitz.medirdv.events.AppointmentRescheduled;
import io.github.slimanitz.medirdv.platform.outbox.OutboxWriter;
import io.github.slimanitz.medirdv.scheduling.SchedulingProperties;
import io.github.slimanitz.medirdv.scheduling.practitioner.AvailabilityRepository;
import io.github.slimanitz.medirdv.scheduling.practitioner.PractitionerRepository;
import io.github.slimanitz.medirdv.scheduling.practitioner.Slot;
import io.github.slimanitz.medirdv.scheduling.practitioner.SlotCalculator;
import io.github.slimanitz.medirdv.scheduling.web.ConflictException;
import io.github.slimanitz.medirdv.scheduling.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Appointment use cases. Every state change writes its event to the outbox in the same transaction. */
@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointments;
    private final PractitionerRepository practitioners;
    private final AvailabilityRepository availabilities;
    private final OutboxWriter outbox;
    private final SchedulingProperties properties;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointments, PractitionerRepository practitioners,
                              AvailabilityRepository availabilities, OutboxWriter outbox,
                              SchedulingProperties properties, Clock clock) {
        this.appointments = appointments;
        this.practitioners = practitioners;
        this.availabilities = availabilities;
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Slot> freeSlots(UUID practitionerId, LocalDate from, LocalDate to) {
        if (!practitioners.existsById(practitionerId)) {
            throw new NotFoundException("Praticien introuvable");
        }
        // Inclusive range: at most maxSlotRangeDays calendar days.
        if (to.isBefore(from) || to.isAfter(from.plusDays(properties.maxSlotRangeDays() - 1L))) {
            throw new IllegalArgumentException("Période invalide (maximum " + properties.maxSlotRangeDays() + " jours)");
        }
        Instant rangeStart = from.atStartOfDay(properties.zone()).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(properties.zone()).toInstant();
        List<Slot> busy = appointments
                .findForPractitioner(practitionerId, rangeStart, rangeEnd, AppointmentStatus.ACTIVE).stream()
                .map(a -> new Slot(a.getStart(), a.getEnd()))
                .toList();
        return SlotCalculator.freeSlots(availabilities.findByPractitionerId(practitionerId), busy, from, to,
                properties.zone(), clock.instant());
    }

    /** Saga step 1: reserve the slot and emit AppointmentRequested. */
    @Transactional
    public Appointment request(UUID patientId, UUID practitionerId, Instant start, String reason) {
        Slot slot = findFreeSlot(practitionerId, start);
        Appointment appointment = Appointment.request(patientId, practitionerId, slot.start(), slot.end(), reason,
                clock.instant());
        try {
            appointments.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            // Partial unique index (practitioner_id, start_at) for active appointments.
            throw new ConflictException("Ce créneau vient d'être réservé, veuillez en choisir un autre.");
        }
        outbox.record(new AppointmentRequested(appointment.getId(), patientId, practitionerId, slot.start(),
                slot.end()));
        log.info("Appointment {} requested for patient {} with practitioner {}", appointment.getId(), patientId,
                practitionerId);
        return appointment;
    }

    /** Saga step 3 (success): the patient was validated. Late or duplicate replies are ignored. */
    @Transactional
    public void onPatientValidated(UUID appointmentId) {
        Optional<Appointment> found = appointments.findById(appointmentId);
        if (found.isEmpty() || !found.get().isPending()) {
            log.info("Ignoring PatientValidated for appointment {} (not pending)", appointmentId);
            return;
        }
        Appointment appointment = found.get();
        appointment.confirm(clock.instant());
        outbox.record(new AppointmentConfirmed(appointment.getId(), appointment.getPatientId(),
                appointment.getPractitionerId(), appointment.getStart(), appointment.getEnd()));
        log.info("Appointment {} confirmed", appointmentId);
    }

    /** Saga step 3 (compensation): release the slot. */
    @Transactional
    public void onPatientRejected(UUID appointmentId, RejectionReason reason) {
        appointments.findById(appointmentId)
                .filter(Appointment::isPending)
                .ifPresentOrElse(a -> reject(a, reason),
                        () -> log.info("Ignoring PatientRejected for appointment {} (not pending)", appointmentId));
    }

    /** Saga timeout: rejects one expired pending appointment (own transaction per appointment). */
    @Transactional
    public void expire(UUID appointmentId) {
        appointments.findById(appointmentId).filter(Appointment::isPending)
                .ifPresent(a -> reject(a, RejectionReason.VALIDATION_TIMEOUT));
    }

    private void reject(Appointment appointment, RejectionReason reason) {
        appointment.reject(reason, clock.instant());
        outbox.record(new AppointmentRejected(appointment.getId(), appointment.getPatientId(),
                appointment.getPractitionerId(), appointment.getStart(), reason));
        log.info("Appointment {} rejected: {}", appointment.getId(), reason);
    }

    /** The free-text {@code comment} is stored here only; the event carries the reason code. */
    @Transactional
    public Appointment cancel(UUID appointmentId, CancelledBy cancelledBy, CancellationReason reason,
                              String comment) {
        Appointment appointment = get(appointmentId);
        cancel(appointment, cancelledBy, reason, comment);
        return appointment;
    }

    private void cancel(Appointment appointment, CancelledBy cancelledBy, CancellationReason reason,
                        String comment) {
        appointment.cancel(reason, comment, clock.instant());
        outbox.record(new AppointmentCancelled(appointment.getId(), appointment.getPatientId(),
                appointment.getPractitionerId(), appointment.getStart(), cancelledBy, reason));
        log.info("Appointment {} cancelled by {}", appointment.getId(), cancelledBy);
    }

    /** PatientDeactivated: cancels every future active appointment of the patient. */
    @Transactional
    public int cancelFutureAppointmentsOf(UUID patientId) {
        List<Appointment> future = appointments.findByPatientIdAndStatusInAndStartAfter(patientId,
                AppointmentStatus.ACTIVE, clock.instant());
        future.forEach(a -> cancel(a, CancelledBy.SYSTEM, CancellationReason.PATIENT_DEACTIVATED, null));
        return future.size();
    }

    @Transactional
    public Appointment reschedule(UUID appointmentId, Instant newStart) {
        Appointment appointment = get(appointmentId);
        Instant previousStart = appointment.getStart();
        Slot slot = findFreeSlot(appointment.getPractitionerId(), newStart);
        appointment.reschedule(slot.start(), slot.end(), clock.instant());
        try {
            appointments.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Ce créneau vient d'être réservé, veuillez en choisir un autre.");
        }
        outbox.record(new AppointmentRescheduled(appointment.getId(), appointment.getPatientId(),
                appointment.getPractitionerId(), previousStart, slot.start(), slot.end()));
        return appointment;
    }

    @Transactional(readOnly = true)
    public Appointment get(UUID appointmentId) {
        return appointments.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Rendez-vous introuvable"));
    }

    @Transactional(readOnly = true)
    public List<Appointment> forPractitioner(UUID practitionerId, Instant from, Instant to) {
        return appointments.findForPractitioner(practitionerId, from, to, EnumSet.allOf(AppointmentStatus.class));
    }

    @Transactional(readOnly = true)
    public List<Appointment> forPatient(UUID patientId) {
        return appointments.findByPatientIdOrderByStartDesc(patientId);
    }

    /** Appointments of one patient with one practitioner (a practitioner's view of a patient). */
    @Transactional(readOnly = true)
    public List<Appointment> forPatientAndPractitioner(UUID patientId, UUID practitionerId) {
        return appointments.findByPatientIdAndPractitionerIdOrderByStartDesc(patientId, practitionerId);
    }

    @Transactional(readOnly = true)
    public List<UUID> expiredPendingIds() {
        Instant threshold = clock.instant().minus(properties.validationTimeout());
        return appointments.findByStatusAndCreatedAtBefore(AppointmentStatus.PENDING_VALIDATION, threshold).stream()
                .map(Appointment::getId).toList();
    }

    private Slot findFreeSlot(UUID practitionerId, Instant start) {
        LocalDate day = start.atZone(properties.zone()).toLocalDate();
        return freeSlots(practitionerId, day, day).stream()
                .filter(s -> s.start().equals(start))
                .findFirst()
                .orElseThrow(() -> new ConflictException("Ce créneau n'est pas disponible."));
    }
}
