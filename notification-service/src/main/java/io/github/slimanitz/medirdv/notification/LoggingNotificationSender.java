package io.github.slimanitz.medirdv.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub sender: logs instead of e-mailing. A real implementation would resolve the patient's
 * address from patient-service at send time, so the address is never copied here.
 */
@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(Notification notification) {
        log.info("[{}] {} notification {} sent to patient {} for appointment {}", notification.getChannel(),
                notification.getType(), notification.getId(), notification.getPatientId(),
                notification.getAppointmentId());
    }
}
