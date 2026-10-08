package io.github.slimanitz.medirdv.patient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Patient record. Personal data stays in this service's database and is masked in logs. */
@Entity
@Table(name = "patients")
public class Patient {

    @Id
    private UUID id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PatientStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Patient() {
    }

    public Patient(UUID id, String firstName, String lastName, String email, String phone, LocalDate birthDate,
                   Instant now) {
        this.id = id;
        this.status = PatientStatus.ACTIVE;
        this.createdAt = now;
        update(firstName, lastName, email, phone, birthDate, now);
    }

    public void update(String firstName, String lastName, String email, String phone, LocalDate birthDate,
                       Instant now) {
        this.firstName = firstName.strip();
        this.lastName = lastName.strip();
        this.email = email.strip().toLowerCase();
        this.phone = phone == null ? null : phone.strip();
        this.birthDate = birthDate;
        this.updatedAt = now;
    }

    /** @return {@code false} if the patient was already inactive. */
    public boolean deactivate(Instant now) {
        if (status == PatientStatus.INACTIVE) {
            return false;
        }
        this.status = PatientStatus.INACTIVE;
        this.updatedAt = now;
        return true;
    }

    public boolean isActive() {
        return status == PatientStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public PatientStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Pseudonymised: safe to log. */
    @Override
    public String toString() {
        return "Patient{id=" + id + ", firstName=" + PersonalDataMasker.name(firstName)
                + ", lastName=" + PersonalDataMasker.name(lastName)
                + ", email=" + PersonalDataMasker.email(email)
                + ", phone=" + PersonalDataMasker.phone(phone)
                + ", status=" + status + "}";
    }
}
