package com.officeshuttle.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("smsNotificationChannel")
public class SmsNotificationChannel implements NotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(SmsNotificationChannel.class);

    @Override
    public String getChannelName() {
        return "SMS";
    }

    @Override
    public void sendNotification(String recipient, String subject, String message) {
        log.info("[SMS SENT] To: {} | Msg: {}", recipient, message);
    }
}
