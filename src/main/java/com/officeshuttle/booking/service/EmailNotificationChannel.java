package com.officeshuttle.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("emailNotificationChannel")
public class EmailNotificationChannel implements NotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    @Override
    public String getChannelName() {
        return "EMAIL";
    }

    @Override
    public void sendNotification(String recipient, String subject, String message) {
        log.info("[EMAIL SENT] To: {} | Subject: {} | Body: {}", recipient, subject, message);
    }
}
