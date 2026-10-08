package io.github.slimanitz.medirdv.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findTop100ByPatientIdOrderByCreatedAtDesc(UUID patientId);

    List<Notification> findTop100ByOrderByCreatedAtDesc();

    List<Notification> findTop100ByPractitionerIdOrderByCreatedAtDesc(UUID practitionerId);

    List<Notification> findTop100ByPatientIdAndPractitionerIdOrderByCreatedAtDesc(UUID patientId,
                                                                                 UUID practitionerId);
}
