package com.kibo.reservation.scheduler;

import com.kibo.reservation.service.ReservationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class HoldExpiryScheduler {
    private final ReservationService reservationService;

    public HoldExpiryScheduler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(fixedDelayString = "${reservation.expiry.scan-delay-ms:5000}")
    public void expireHolds() {
        Instant now = Instant.now();
        for (String holdId : reservationService.findExpiredActiveHoldIds(now)) {
            reservationService.expireHoldIfNeeded(holdId, now);
        }
    }
}
