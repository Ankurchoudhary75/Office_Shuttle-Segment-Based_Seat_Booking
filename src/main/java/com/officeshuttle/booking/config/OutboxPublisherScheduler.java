package com.officeshuttle.booking.config;

import com.officeshuttle.booking.domain.OutboxEvent;
import com.officeshuttle.booking.domain.OutboxStatus;
import com.officeshuttle.booking.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Transactional Outbox Background Publisher:
 * Asynchronously publishes pending domain events to message broker / event stream.
 * Decouples notification / analytics dispatch from primary ACID commit path.
 */
@Component
public class OutboxPublisherScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherScheduler.class);

    private final OutboxEventRepository outboxEventRepository;

    public OutboxPublisherScheduler(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pendingEvents) {
            try {
                // Publish to Kafka / Message Broker / Logging stream
                log.info("[OUTBOX PUBLISHED] Event ID: {} | Type: {} | Aggregate: {} | Payload: {}",
                        event.getEventId(), event.getType(), event.getAggregateId(), event.getPayloadJson());

                event.setStatus(OutboxStatus.PUBLISHED);
                event.setProcessedAt(OffsetDateTime.now());
            } catch (Exception ex) {
                log.error("Failed to publish outbox event {}: {}", event.getEventId(), ex.getMessage());
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() > 5) {
                    event.setStatus(OutboxStatus.FAILED);
                }
            }
            outboxEventRepository.save(event);
        }
    }
}
