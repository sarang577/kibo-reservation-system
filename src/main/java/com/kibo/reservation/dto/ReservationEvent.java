package com.kibo.reservation.dto;

import com.kibo.reservation.entity.Hold;
import java.time.Instant;

public record ReservationEvent(
        String eventId,
        String eventType,
        String holdId,
        Long dropId,
        String customerId,
        int quantity,
        String status,
        Instant occurredAt
) {
    public static ReservationEvent from(Hold hold, String eventType) {
        return new ReservationEvent(null, eventType, hold.getId(), hold.getDrop().getId(),
                hold.getCustomerId(), hold.getQuantity(), hold.getStatus().name(), Instant.now());
    }
}
