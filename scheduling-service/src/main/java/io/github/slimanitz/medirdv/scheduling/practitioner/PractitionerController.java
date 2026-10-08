package io.github.slimanitz.medirdv.scheduling.practitioner;

import io.github.slimanitz.medirdv.scheduling.appointment.AppointmentService;
import io.github.slimanitz.medirdv.scheduling.web.NotFoundException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/practitioners")
public class PractitionerController {

    private final PractitionerRepository practitioners;
    private final AppointmentService appointments;

    public PractitionerController(PractitionerRepository practitioners, AppointmentService appointments) {
        this.practitioners = practitioners;
        this.appointments = appointments;
    }

    public record PractitionerResponse(UUID id, String fullName, String specialty) {
        static PractitionerResponse of(Practitioner p) {
            return new PractitionerResponse(p.getId(), p.getFullName(), p.getSpecialty());
        }
    }

    @GetMapping
    public List<PractitionerResponse> list() {
        return practitioners.findAllByOrderByFullNameAsc().stream().map(PractitionerResponse::of).toList();
    }

    @GetMapping("/{id}")
    public PractitionerResponse get(@PathVariable UUID id) {
        return practitioners.findById(id).map(PractitionerResponse::of)
                .orElseThrow(() -> new NotFoundException("Praticien introuvable"));
    }

    /** Free slots between two dates (inclusive), e.g. {@code ?from=2026-10-12&to=2026-10-18}. */
    @GetMapping("/{id}/slots")
    public List<Slot> slots(@PathVariable UUID id,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return appointments.freeSlots(id, from, to);
    }
}
