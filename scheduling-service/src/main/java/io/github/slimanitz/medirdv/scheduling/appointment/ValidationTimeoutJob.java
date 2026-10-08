package io.github.slimanitz.medirdv.scheduling.appointment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Saga timeout: rejects appointments still waiting for patient validation after the timeout. */
@Component
public class ValidationTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(ValidationTimeoutJob.class);

    private final AppointmentService service;

    public ValidationTimeoutJob(AppointmentService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${medirdv.scheduling.timeout-check-interval-ms:30000}")
    public void rejectExpired() {
        int count = 0;
        for (UUID id : service.expiredPendingIds()) {
            try {
                service.expire(id);
                count++;
            } catch (RuntimeException e) {
                log.warn("Could not expire appointment {}: {}", id, e.getMessage());
            }
        }
        if (count > 0) {
            log.info("Saga timeout: {} pending appointment(s) rejected", count);
        }
    }
}
