package com.kibo.reservation.scheduler;

import com.kibo.reservation.config.RabbitConfig;
import com.kibo.reservation.entity.OutboxEvent;
import com.kibo.reservation.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final int batchSize;

    public OutboxPublisher(OutboxEventRepository repository, RabbitTemplate rabbitTemplate,
                           @Value("${reservation.outbox.batch-size:100}") int batchSize) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${reservation.outbox.scan-delay-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        for (OutboxEvent event : repository.findByPublishedFalseOrderByCreatedAtAsc(PageRequest.of(0, batchSize))) {
            try {
                rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, event.getPayload());
                event.setPublished(true);
                event.setPublishedAt(Instant.now());
                repository.save(event);
            } catch (Exception ex) {
                event.setAttempts(event.getAttempts() + 1);
                repository.save(event);
                log.warn("Could not publish outbox event id {}; will retry later", event.getId(), ex);
            }
        }
    }
}
