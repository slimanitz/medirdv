package io.github.slimanitz.medirdv.scheduling.appointment;

import io.github.slimanitz.medirdv.events.CancellationReason;
import io.github.slimanitz.medirdv.platform.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Access rules: an admin sees everything; a practitioner only the appointments of the
 * practitioner record linked to their account ({@code practitioner_id} claim, mandatory);
 * a patient only their own ({@code patient_id} claim). Every check fails closed.
 */
@RestController
@RequestMapping("/appointments")
public class AppointmentController {

    private static final String NO_PATIENT = "Aucun dossier patient n'est associé à ce compte";
    private static final String NO_PRACTITIONER = "Aucun praticien n'est associé à ce compte";

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    /** {@code patientId} is ignored for patients: their own id comes from the token. */
    public record BookingRequest(@NotNull UUID practitionerId, @NotNull Instant start,
                                 @Size(max = 255) String reason, UUID patientId) {
    }

    public record RescheduleRequest(@NotNull Instant start) {
    }

    public record AppointmentResponse(UUID id, UUID patientId, UUID practitionerId, Instant start, Instant end,
                                      AppointmentStatus status, String reason, String statusReason,
                                      String statusComment, Instant createdAt, Instant updatedAt) {
        static AppointmentResponse of(Appointment a) {
            return new AppointmentResponse(a.getId(), a.getPatientId(), a.getPractitionerId(), a.getStart(),
                    a.getEnd(), a.getStatus(), a.getReason(), a.getStatusReason(), a.getStatusComment(),
                    a.getCreatedAt(), a.getUpdatedAt());
        }
    }

    /** Starts the booking saga. Returns 202: confirmation happens asynchronously. */
    @PostMapping
    public ResponseEntity<AppointmentResponse> book(@Valid @RequestBody BookingRequest request,
                                                    Authentication authentication) {
        CurrentUser user = CurrentUser.from(authentication);
        UUID patientId;
        if (user.isAdmin()) {
            patientId = requirePatientId(request.patientId());
        } else if (user.isPractitioner()) {
            UUID own = user.practitionerId().orElseThrow(() -> new AccessDeniedException(NO_PRACTITIONER));
            if (!own.equals(request.practitionerId())) {
                throw new AccessDeniedException("Un praticien ne peut réserver que dans son propre agenda");
            }
            patientId = requirePatientId(request.patientId());
        } else {
            patientId = user.patientId().orElseThrow(() -> new AccessDeniedException(NO_PATIENT));
        }
        Appointment appointment = service.request(patientId, request.practitionerId(), request.start(),
                request.reason());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(AppointmentResponse.of(appointment));
    }

    private static UUID requirePatientId(UUID patientId) {
        if (patientId == null) {
            throw new IllegalArgumentException("patientId est obligatoire");
        }
        return patientId;
    }

    /**
     * Admin: {@code ?practitionerId=&from=&to=} (agenda) or {@code ?patientId=}.
     * Practitioner: their own agenda ({@code practitionerId} defaults to theirs, another one is
     * refused), or a patient's appointments with them ({@code ?patientId=}).
     * Patient: their own appointments (parameters ignored).
     */
    @GetMapping
    public List<AppointmentResponse> search(@RequestParam(required = false) UUID practitionerId,
                                            @RequestParam(required = false) UUID patientId,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                            Authentication authentication) {
        CurrentUser user = CurrentUser.from(authentication);
        List<Appointment> result;
        if (user.isAdmin()) {
            if (practitionerId != null) {
                result = agenda(practitionerId, from, to);
            } else if (patientId != null) {
                result = service.forPatient(patientId);
            } else {
                throw new IllegalArgumentException("practitionerId ou patientId est obligatoire");
            }
        } else if (user.isPractitioner()) {
            UUID own = user.practitionerId().orElseThrow(() -> new AccessDeniedException(NO_PRACTITIONER));
            if (practitionerId != null && !practitionerId.equals(own)) {
                throw new AccessDeniedException("Accès refusé à l'agenda d'un autre praticien");
            }
            result = patientId != null ? service.forPatientAndPractitioner(patientId, own) : agenda(own, from, to);
        } else {
            result = service.forPatient(user.patientId().orElseThrow(() -> new AccessDeniedException(NO_PATIENT)));
        }
        return result.stream().map(AppointmentResponse::of).toList();
    }

    private List<Appointment> agenda(UUID practitionerId, Instant from, Instant to) {
        Instant start = from != null ? from : Instant.now().minusSeconds(7 * 86400L);
        Instant end = to != null ? to : start.plusSeconds(35 * 86400L);
        return service.forPractitioner(practitionerId, start, end);
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(@PathVariable UUID id, Authentication authentication) {
        Appointment appointment = service.get(id);
        checkAccess(appointment, CurrentUser.from(authentication));
        return AppointmentResponse.of(appointment);
    }

    /**
     * Cancels with a coded reason. The optional {@code comment} is kept in this service only:
     * the {@code AppointmentCancelled} event carries the code, never free text.
     */
    @DeleteMapping("/{id}")
    public AppointmentResponse cancel(@PathVariable UUID id,
                                      @RequestParam(required = false) CancellationReason reasonCode,
                                      @RequestParam(required = false) @Size(max = 500) String comment,
                                      Authentication authentication) {
        CurrentUser user = CurrentUser.from(authentication);
        checkAccess(service.get(id), user);
        CancellationReason code = reasonCode != null ? reasonCode
                : user.isPractitioner() ? CancellationReason.PRACTITIONER_UNAVAILABLE
                : user.isAdmin() ? CancellationReason.OTHER : CancellationReason.PATIENT_REQUEST;
        if (code == CancellationReason.PATIENT_DEACTIVATED) {
            throw new IllegalArgumentException("Ce motif est réservé au système");
        }
        return AppointmentResponse.of(service.cancel(id, user.actor(), code, comment));
    }

    @PatchMapping("/{id}")
    public AppointmentResponse reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest request,
                                          Authentication authentication) {
        checkAccess(service.get(id), CurrentUser.from(authentication));
        return AppointmentResponse.of(service.reschedule(id, request.start()));
    }

    /** Fail-closed ownership check. */
    static void checkAccess(Appointment appointment, CurrentUser user) {
        if (user.isAdmin()) {
            return;
        }
        boolean ownPatient = user.patientId().map(appointment.getPatientId()::equals).orElse(false);
        boolean ownPractitioner = user.isPractitioner()
                && user.practitionerId().map(appointment.getPractitionerId()::equals).orElse(false);
        if (!ownPatient && !ownPractitioner) {
            throw new AccessDeniedException("Accès refusé à ce rendez-vous");
        }
    }
}
