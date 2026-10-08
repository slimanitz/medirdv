package io.github.slimanitz.medirdv.scheduling.practitioner;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AvailabilityRepository extends JpaRepository<Availability, UUID> {

    List<Availability> findByPractitionerId(UUID practitionerId);
}
