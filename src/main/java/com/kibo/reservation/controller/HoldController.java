package com.kibo.reservation.controller;

import com.kibo.reservation.dto.CreateHoldRequest;
import com.kibo.reservation.dto.HoldResponse;
import com.kibo.reservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class HoldController {
    private final ReservationService reservationService;

    public HoldController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/drops/{dropId}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldResponse createHold(@PathVariable Long dropId,
                                   @Valid @RequestBody CreateHoldRequest request) {
        return HoldResponse.from(reservationService.createHold(dropId, request));
    }

    @PostMapping("/holds/{holdId}/confirm")
    public HoldResponse confirmHold(@PathVariable String holdId) {
        var hold = reservationService.confirmHold(holdId);
        if (hold.getStatus().name().equals("EXPIRED")) {
            throw new com.kibo.reservation.exception.ReservationConflictException(
                    "This hold has expired and its inventory has been released");
        }
        return HoldResponse.from(hold);
    }

    @PostMapping("/holds/{holdId}/cancel")
    public HoldResponse cancelHold(@PathVariable String holdId) {
        return HoldResponse.from(reservationService.cancelHold(holdId));
    }

    @GetMapping("/holds/{holdId}")
    public HoldResponse getHold(@PathVariable String holdId) {
        return HoldResponse.from(reservationService.getHold(holdId));
    }
}
