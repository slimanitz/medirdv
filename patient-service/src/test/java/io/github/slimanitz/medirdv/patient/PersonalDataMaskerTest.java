package io.github.slimanitz.medirdv.patient;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalDataMaskerTest {

    @Test
    void masksEmailPhoneAndNames() {
        assertThat(PersonalDataMasker.email("alice.durand@example.fr")).isEqualTo("a***@example.fr");
        assertThat(PersonalDataMasker.email("invalid")).isEqualTo("***");
        assertThat(PersonalDataMasker.phone("06 12 34 56 78")).isEqualTo("********78");
        assertThat(PersonalDataMasker.phone(null)).isNull();
        assertThat(PersonalDataMasker.name("Durand")).isEqualTo("D.");
    }

    @Test
    void patientToStringNeverLeaksPersonalData() {
        Patient patient = new Patient(UUID.randomUUID(), "Alice", "Durand", "Alice.Durand@Example.fr",
                "06 12 34 56 78", LocalDate.of(1990, 4, 12), Instant.now());

        String logged = patient.toString();

        assertThat(logged).contains("a***@example.fr", "********78", "A.", "D.")
                .doesNotContain("Alice").doesNotContain("Durand").doesNotContain("56 78").doesNotContain("1990");
        assertThat(patient.getEmail()).isEqualTo("alice.durand@example.fr");
    }

    @Test
    void deactivationIsIdempotent() {
        Patient patient = new Patient(UUID.randomUUID(), "A", "B", "a@b.fr", null, LocalDate.of(2000, 1, 1),
                Instant.now());

        assertThat(patient.deactivate(Instant.now())).isTrue();
        assertThat(patient.deactivate(Instant.now())).isFalse();
        assertThat(patient.isActive()).isFalse();
    }
}
