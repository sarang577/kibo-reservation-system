package com.kibo.reservation.controller;

import com.kibo.reservation.dto.DropResponse;
import com.kibo.reservation.service.ReservationService;
import org.springframework.web.bind.annotation.*;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;

@RestController
@RequestMapping("/api/drops")
public class DropController {
    private final ReservationService reservationService;

    public DropController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping
    @Cacheable(cacheNames = "drops")
    public List<DropResponse> listDrops() {
        return reservationService.listDrops().stream()
                .map(DropResponse::from)
                .toList();
    }

    @GetMapping("/{dropId}")
    @Cacheable(cacheNames = "drop", key = "#dropId")
    public DropResponse getDrop(@PathVariable Long dropId) {
        return DropResponse.from(reservationService.getDrop(dropId));
    }
}
