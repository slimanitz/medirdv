package io.github.slimanitz.medirdv.notification;

/** Delivery channel abstraction (e-mail provider, SMS gateway...). */
public interface NotificationSender {

    void send(Notification notification);
}
