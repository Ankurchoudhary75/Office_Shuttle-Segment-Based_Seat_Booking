package com.officeshuttle.booking.service;

/**
 * Observer Pattern Interface for pluggable notification delivery channels.
 */
public interface NotificationChannel {
    String getChannelName();
    void sendNotification(String recipient, String subject, String message);
}
