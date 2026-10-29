package com.kibo.reservation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kibo.reservation.dto.ReservationEvent;
import com.kibo.reservation.entity.Hold;
import com.kibo.reservation.entity.OutboxEvent;
import com.kibo.reservation.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;

@Service
public class OutboxService {
    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // Called from the reservation transaction: the business change and event row commit together.
    public void record(Hold hold, String eventType) {
        try {
            OutboxEvent event = new OutboxEvent();
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(ReservationEvent.from(hold, eventType)));
            repository.save(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize reservation event", e);
        }
    }
}
