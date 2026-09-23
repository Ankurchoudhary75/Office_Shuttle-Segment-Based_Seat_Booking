package com.officeshuttle.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("pushNotificationChannel")
public class PushNotificationChannel implements NotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(PushNotificationChannel.class);

    @Override
    public String getChannelName() {
        return "PUSH";
    }

    @Override
    public void sendNotification(String recipient, String subject, String message) {
        log.info("[PUSH NOTIFICATION SENT] To: {} | Title: {} | Alert: {}", recipient, subject, message);
    }
}
