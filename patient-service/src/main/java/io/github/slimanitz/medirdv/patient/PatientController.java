package io.github.slimanitz.medirdv.patient;

import io.github.slimanitz.medirdv.platform.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/patients")
public class PatientController {

    private final PatientService service;

    public PatientController(PatientService service) {
        this.service = service;
    }

    public record PatientRequest(@NotBlank @Size(max = 80) String firstName,
                                 @NotBlank @Size(max = 80) String lastName,
                                 @NotBlank @Email @Size(max = 160) String email,
                                 @Pattern(regexp = "^[+0-9 .()-]{6,20}$") String phone,
                                 @NotNull @Past LocalDate birthDate) {
        PatientService.PatientData toData() {
            return new PatientService.PatientData(firstName, lastName, email, phone, birthDate);
        }
    }

    public record PatientResponse(UUID id, String firstName, String lastName, String email, String phone,
                                  LocalDate birthDate, PatientStatus status, Instant createdAt) {
        static PatientResponse of(Patient p) {
            return new PatientResponse(p.getId(), p.getFirstName(), p.getLastName(), p.getEmail(), p.getPhone(),
                    p.getBirthDate(), p.getStatus(), p.getCreatedAt());
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('admin', 'practitioner')")
    public List<PatientResponse> list() {
        return service.list().stream().map(PatientResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('admin', 'practitioner')")
    public PatientResponse create(@Valid @RequestBody PatientRequest request) {
        return PatientResponse.of(service.create(request.toData()));
    }

    /** The record linked to the logged-in patient (via the {@code patient_id} token claim). */
    @GetMapping("/me")
    public PatientResponse me(Authentication authentication) {
        UUID id = CurrentUser.from(authentication).patientId()
                .orElseThrow(() -> new AccessDeniedException("Aucun dossier patient n'est associé à ce compte"));
        return PatientResponse.of(service.get(id));
    }

    @GetMapping("/{id}")
    public PatientResponse get(@PathVariable UUID id, Authentication authentication) {
        checkAccess(id, authentication);
        return PatientResponse.of(service.get(id));
    }

    @PutMapping("/{id}")
    public PatientResponse update(@PathVariable UUID id, @Valid @RequestBody PatientRequest request,
                                  Authentication authentication) {
        checkAccess(id, authentication);
        return PatientResponse.of(service.update(id, request.toData()));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('admin', 'practitioner')")
    public PatientResponse deactivate(@PathVariable UUID id) {
        return PatientResponse.of(service.deactivate(id));
    }

    private static void checkAccess(UUID id, Authentication authentication) {
        if (!CurrentUser.from(authentication).canAccessPatient(id)) {
            throw new AccessDeniedException("Accès refusé à ce dossier patient");
        }
    }

    @ExceptionHandler(PatientService.PatientNotFoundException.class)
    ProblemDetail notFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(PatientService.DuplicateEmailException.class)
    ProblemDetail duplicate(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
