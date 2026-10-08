package io.github.slimanitz.medirdv.scheduling.appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    @Query("""
            select a from Appointment a
            where a.practitionerId = :practitionerId and a.status in :statuses
              and a.start < :to and a.end > :from
            order by a.start
            """)
    List<Appointment> findForPractitioner(UUID practitionerId, Instant from, Instant to,
                                          Collection<AppointmentStatus> statuses);

    List<Appointment> findByPatientIdOrderByStartDesc(UUID patientId);

    List<Appointment> findByPatientIdAndPractitionerIdOrderByStartDesc(UUID patientId, UUID practitionerId);

    List<Appointment> findByPatientIdAndStatusInAndStartAfter(UUID patientId, Collection<AppointmentStatus> statuses,
                                                             Instant after);

    List<Appointment> findByStatusAndCreatedAtBefore(AppointmentStatus status, Instant before);
}
