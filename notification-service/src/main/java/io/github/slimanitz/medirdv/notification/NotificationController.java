package io.github.slimanitz.medirdv.notification;

import io.github.slimanitz.medirdv.platform.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    public record NotificationResponse(UUID id, UUID patientId, UUID appointmentId, NotificationType type,
                                       Notification.Channel channel, Notification.Status status, String body,
                                       Instant createdAt) {
        static NotificationResponse of(Notification n) {
            return new NotificationResponse(n.getId(), n.getPatientId(), n.getAppointmentId(), n.getType(),
                    n.getChannel(), n.getStatus(), n.getBody(), n.getCreatedAt());
        }
    }

    /**
     * Admin: latest notifications or one patient's. Practitioner: notifications of their own
     * appointments (optionally for one patient). Patient: their own. Fails closed.
     */
    @GetMapping
    public List<NotificationResponse> list(@RequestParam(required = false) UUID patientId,
                                           Authentication authentication) {
        CurrentUser user = CurrentUser.from(authentication);
        List<Notification> result;
        if (user.isAdmin()) {
            result = patientId == null ? service.latest() : service.forPatient(patientId);
        } else if (user.isPractitioner()) {
            UUID own = user.practitionerId()
                    .orElseThrow(() -> new AccessDeniedException("Aucun praticien n'est associé à ce compte"));
            result = patientId == null ? service.forPractitioner(own)
                    : service.forPatientAndPractitioner(patientId, own);
        } else {
            UUID own = user.patientId()
                    .orElseThrow(() -> new AccessDeniedException("Aucun dossier patient n'est associé à ce compte"));
            if (patientId != null && !patientId.equals(own)) {
                throw new AccessDeniedException("Accès refusé");
            }
            result = service.forPatient(own);
        }
        return result.stream().map(NotificationResponse::of).toList();
    }
}
