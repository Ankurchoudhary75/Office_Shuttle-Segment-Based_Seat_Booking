package com.officeshuttle.booking.config;

import com.officeshuttle.booking.domain.OutboxStatus;
import com.officeshuttle.booking.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    public MetricsConfig(MeterRegistry meterRegistry, OutboxEventRepository outboxEventRepository) {
        // Expose outbox backlog gauge
        Gauge.builder("outbox_backlog_size", outboxEventRepository,
                repo -> repo.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING).size())
                .description("Number of pending transactional outbox events awaiting publication")
                .register(meterRegistry);
    }
}
