package com.officeshuttle.booking.service;

import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Publishes domain events to all registered NotificationChannel observers.
 */
@Service
public class NotificationService {

    private final List<NotificationChannel> channels;

    public NotificationService(List<NotificationChannel> channels) {
        this.channels = channels;
    }

    public void notifyAllChannels(String recipient, String subject, String message) {
        for (NotificationChannel channel : channels) {
            try {
                channel.sendNotification(recipient, subject, message);
            } catch (Exception ignored) {}
        }
    }
}
