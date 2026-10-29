package com.kibo.reservation.service;

import com.kibo.reservation.dto.CreateHoldRequest;
import com.kibo.reservation.entity.Drop;
import com.kibo.reservation.entity.Hold;
import com.kibo.reservation.entity.HoldStatus;
import com.kibo.reservation.exception.DropNotFoundException;
import com.kibo.reservation.exception.HoldNotFoundException;
import com.kibo.reservation.exception.ReservationConflictException;
import com.kibo.reservation.repository.DropRepository;
import com.kibo.reservation.repository.HoldRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class ReservationService {
    private static final Duration HOLD_DURATION = Duration.ofMinutes(5);

    private final DropRepository dropRepository;
    private final HoldRepository holdRepository;
    private final OutboxService outboxService;

    public ReservationService(DropRepository dropRepository, HoldRepository holdRepository, OutboxService outboxService) {
        this.dropRepository = dropRepository;
        this.holdRepository = holdRepository;
        this.outboxService = outboxService;
    }

    @Transactional
    @CacheEvict(cacheNames = {"drops", "drop"}, allEntries = true)
    public Hold createHold(Long dropId, CreateHoldRequest request) {
        Instant now = Instant.now();

        // Lock the drop row so competing requests cannot claim the same inventory.
        Drop drop = dropRepository.findByIdForUpdate(dropId)
                .orElseThrow(() -> new DropNotFoundException(dropId));

        if (now.isBefore(drop.getStartsAt())) {
            throw new ReservationConflictException("This drop has not opened yet");
        }
        if (!now.isBefore(drop.getEndsAt())) {
            throw new ReservationConflictException("This drop is closed");
        }
        if (request.quantity() <= 0) {
            throw new ReservationConflictException("Quantity must be greater than zero");
        }
        if (drop.getAvailableQuantity() < request.quantity()) {
            throw new ReservationConflictException("Insufficient inventory");
        }

        drop.setAvailableQuantity(drop.getAvailableQuantity() - request.quantity());

        Hold hold = new Hold();
        hold.setDrop(drop);
        hold.setCustomerId(request.customerId());
        hold.setQuantity(request.quantity());
        hold.setStatus(HoldStatus.ACTIVE);
        hold.setCreatedAt(now);
        hold.setUpdatedAt(now);
        hold.setExpiresAt(now.plus(HOLD_DURATION));

        Hold saved = holdRepository.save(hold);
        outboxService.record(saved, "HOLD_CREATED");
        return saved;
    }

    @Transactional
    @CacheEvict(cacheNames = {"drops", "drop"}, allEntries = true)
    public Hold confirmHold(String holdId) {
        Hold hold = getHoldForUpdate(holdId);
        Instant now = Instant.now();

        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new ReservationConflictException(
                    "Only an active hold can be confirmed; current status is " + hold.getStatus());
        }

        if (!now.isBefore(hold.getExpiresAt())) {
            releaseInventoryAndExpire(hold, now);
            outboxService.record(hold, "HOLD_EXPIRED");
            // Returning a conflict rather than throwing inside this transaction is intentional:
            // the EXPIRED status and inventory release must commit.
            return hold;
        }

        hold.setStatus(HoldStatus.CONFIRMED);
        hold.setUpdatedAt(now);
        Hold saved = holdRepository.save(hold);
        outboxService.record(saved, "HOLD_CONFIRMED");
        return saved;
    }

    @Transactional
    @CacheEvict(cacheNames = {"drops", "drop"}, allEntries = true)
    public Hold cancelHold(String holdId) {
        Hold hold = getHoldForUpdate(holdId);
        Instant now = Instant.now();

        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new ReservationConflictException(
                    "Only an active hold can be cancelled; current status is " + hold.getStatus());
        }

        releaseInventory(hold);
        hold.setStatus(HoldStatus.CANCELLED);
        hold.setUpdatedAt(now);
        Hold saved = holdRepository.save(hold);
        outboxService.record(saved, "HOLD_CANCELLED");
        return saved;
    }

    @Transactional(readOnly = true)
    public Hold getHold(String holdId) {
        return holdRepository.findById(holdId)
                .orElseThrow(() -> new HoldNotFoundException(holdId));
    }

    @Transactional(readOnly = true)
    public List<Drop> listDrops() {
        return dropRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Drop getDrop(Long dropId) {
        return dropRepository.findById(dropId)
                .orElseThrow(() -> new DropNotFoundException(dropId));
    }

    @Transactional
    @CacheEvict(cacheNames = {"drops", "drop"}, allEntries = true)
    public void expireHoldIfNeeded(String holdId, Instant now) {
        Hold hold = holdRepository.findByIdForUpdate(holdId)
                .orElse(null);
        if (hold == null || hold.getStatus() != HoldStatus.ACTIVE
                || hold.getExpiresAt().isAfter(now)) {
            return;
        }

        releaseInventory(hold);
        hold.setStatus(HoldStatus.EXPIRED);
        hold.setUpdatedAt(now);
        Hold saved = holdRepository.save(hold);
        outboxService.record(saved, "HOLD_EXPIRED");
    }

    @Transactional(readOnly = true)
    public List<String> findExpiredActiveHoldIds(Instant now) {
        return holdRepository
                .findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(HoldStatus.ACTIVE, now)
                .stream()
                .map(Hold::getId)
                .toList();
    }

    private Hold getHoldForUpdate(String holdId) {
        return holdRepository.findByIdForUpdate(holdId)
                .orElseThrow(() -> new HoldNotFoundException(holdId));
    }

    private void releaseInventoryAndExpire(Hold hold, Instant now) {
        releaseInventory(hold);
        hold.setStatus(HoldStatus.EXPIRED);
        hold.setUpdatedAt(now);
        holdRepository.save(hold);
    }

    private void releaseInventory(Hold hold) {
        // Lock the drop row before updating its available quantity.
        Drop drop = dropRepository.findByIdForUpdate(hold.getDrop().getId())
                .orElseThrow(() -> new DropNotFoundException(hold.getDrop().getId()));

        int newAvailable = drop.getAvailableQuantity() + hold.getQuantity();
        if (newAvailable > drop.getTotalQuantity()) {
            throw new IllegalStateException("Inventory invariant violated for drop " + drop.getId());
        }
        drop.setAvailableQuantity(newAvailable);
    }
}
