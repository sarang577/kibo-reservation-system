package com.kibo.reservation.dto;

import com.kibo.reservation.entity.Hold;
import com.kibo.reservation.entity.HoldStatus;
import java.time.Instant;

public record HoldResponse(
        String holdId,
        Long dropId,
        String customerId,
        int quantity,
        HoldStatus status,
        Instant createdAt,
        Instant expiresAt
) {
    public static HoldResponse from(Hold hold) {
        return new HoldResponse(hold.getId(), hold.getDrop().getId(),
                hold.getCustomerId(), hold.getQuantity(), hold.getStatus(),
                hold.getCreatedAt(), hold.getExpiresAt());
    }
}
