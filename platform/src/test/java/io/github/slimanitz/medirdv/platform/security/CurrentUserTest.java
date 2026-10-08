package io.github.slimanitz.medirdv.platform.security;

import io.github.slimanitz.medirdv.events.CancelledBy;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserTest {

    private Jwt jwt(List<String> roles, String patientId) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("preferred_username", "alice")
                .claim("realm_access", Map.of("roles", roles));
        if (patientId != null) {
            builder.claim("patient_id", patientId);
        }
        return builder.build();
    }

    @Test
    void mapsRealmRolesAndPatientAttribute() {
        UUID patientId = UUID.randomUUID();
        var auth = new KeycloakRolesConverter().convert(jwt(List.of("patient", "offline_access"), patientId.toString()));

        CurrentUser user = CurrentUser.from(auth);

        assertThat(user.username()).isEqualTo("alice");
        assertThat(user.roles()).containsExactlyInAnyOrder("patient", "offline_access");
        assertThat(user.patientId()).contains(patientId);
        assertThat(user.isStaff()).isFalse();
        assertThat(user.canAccessPatient(patientId)).isTrue();
        assertThat(user.canAccessPatient(UUID.randomUUID())).isFalse();
        assertThat(user.actor()).isEqualTo(CancelledBy.PATIENT);
    }

    @Test
    void staffCanAccessAnyPatient() {
        CurrentUser user = CurrentUser.from(new KeycloakRolesConverter().convert(jwt(List.of("practitioner"), null)));

        assertThat(user.isStaff()).isTrue();
        assertThat(user.canAccessPatient(UUID.randomUUID())).isTrue();
        assertThat(user.actor()).isEqualTo(CancelledBy.PRACTITIONER);
    }

    @Test
    void toleratesTokensWithoutRolesOrWithInvalidAttribute() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "x").claim("patient_id", "nope").build();

        CurrentUser user = CurrentUser.from(new KeycloakRolesConverter().convert(jwt));

        assertThat(user.roles()).isEmpty();
        assertThat(user.patientId()).isEmpty();
    }
}
