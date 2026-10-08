package io.github.slimanitz.medirdv.platform.security;

import io.github.slimanitz.medirdv.events.CancelledBy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Identity extracted from the Keycloak access token. {@code patient_id} and
 * {@code practitioner_id} are user attributes mapped into the token by the realm.
 */
public record CurrentUser(String username, Set<String> roles, Optional<UUID> patientId,
                          Optional<UUID> practitionerId) {

    public static final String ADMIN = "admin";
    public static final String PRACTITIONER = "practitioner";
    public static final String PATIENT = "patient";

    public static CurrentUser from(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .collect(Collectors.toUnmodifiableSet());
        Optional<UUID> patientId = Optional.empty();
        Optional<UUID> practitionerId = Optional.empty();
        if (authentication instanceof JwtAuthenticationToken token) {
            patientId = uuidClaim(token, "patient_id");
            practitionerId = uuidClaim(token, "practitioner_id");
        }
        return new CurrentUser(authentication.getName(), roles, patientId, practitionerId);
    }

    private static Optional<UUID> uuidClaim(JwtAuthenticationToken token, String claim) {
        String value = token.getToken().getClaimAsString(claim);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public boolean isStaff() {
        return roles.contains(ADMIN) || roles.contains(PRACTITIONER);
    }

    public boolean isAdmin() {
        return roles.contains(ADMIN);
    }

    /** Staff can act on any patient; a patient only on the record linked to their account. */
    public boolean canAccessPatient(UUID id) {
        return isStaff() || patientId.map(id::equals).orElse(false);
    }

    public boolean isPractitioner() {
        return roles.contains(PRACTITIONER);
    }

    /** Actor written in cancellation events (role, never a name). */
    public CancelledBy actor() {
        if (isAdmin()) {
            return CancelledBy.ADMIN;
        }
        if (isPractitioner()) {
            return CancelledBy.PRACTITIONER;
        }
        return CancelledBy.PATIENT;
    }
}
