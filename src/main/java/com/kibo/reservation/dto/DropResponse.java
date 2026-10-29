package com.kibo.reservation.dto;

import com.kibo.reservation.entity.Drop;
import java.time.Instant;

public record DropResponse(
        Long id,
        String name,
        int totalQuantity,
        int availableQuantity,
        Instant startsAt,
        Instant endsAt
) {
    public static DropResponse from(Drop drop) {
        return new DropResponse(drop.getId(), drop.getName(),
                drop.getTotalQuantity(), drop.getAvailableQuantity(),
                drop.getStartsAt(), drop.getEndsAt());
    }
}
