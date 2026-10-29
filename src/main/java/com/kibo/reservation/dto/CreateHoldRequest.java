package com.kibo.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateHoldRequest(
        @NotBlank(message = "customerId is required") String customerId,
        @Positive(message = "quantity must be greater than zero") int quantity
) {}
