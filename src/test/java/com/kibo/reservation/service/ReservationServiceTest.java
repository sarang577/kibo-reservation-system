package com.kibo.reservation.service;

import com.kibo.reservation.dto.CreateHoldRequest;
import com.kibo.reservation.entity.Drop;
import com.kibo.reservation.entity.Hold;
import com.kibo.reservation.entity.HoldStatus;
import com.kibo.reservation.exception.ReservationConflictException;
import com.kibo.reservation.repository.DropRepository;
import com.kibo.reservation.repository.HoldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private DropRepository dropRepository;

    @Mock
    private HoldRepository holdRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private ReservationService reservationService;

    private Drop drop;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        drop = new Drop();
        drop.setId(1L);
        drop.setName("Test Drop");
        drop.setTotalQuantity(10);
        drop.setAvailableQuantity(10);
        drop.setStartsAt(now.minus(1, ChronoUnit.HOURS));
        drop.setEndsAt(now.plus(1, ChronoUnit.HOURS));
    }

    @Test
    void createHoldReducesAvailableInventory() {
        when(dropRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(drop));
        when(holdRepository.save(any(Hold.class))).thenAnswer(invocation -> {
            Hold hold = invocation.getArgument(0);
            hold.setId("hold-1");
            return hold;
        });

        Hold result = reservationService.createHold(1L, new CreateHoldRequest("customer-1", 3));

        assertEquals(HoldStatus.ACTIVE, result.getStatus());
        assertEquals(3, result.getQuantity());
        assertEquals(7, drop.getAvailableQuantity());
        verify(holdRepository).save(any(Hold.class));
    }

    @Test
    void createHoldRejectsWhenInventoryIsInsufficient() {
        drop.setAvailableQuantity(1);
        when(dropRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(drop));

        assertThrows(ReservationConflictException.class,
                () -> reservationService.createHold(1L, new CreateHoldRequest("customer-1", 2)));

        assertEquals(1, drop.getAvailableQuantity());
        verify(holdRepository, never()).save(any(Hold.class));
    }

    @Test
    void cancelActiveHoldRestoresInventoryExactlyOnce() {
        drop.setAvailableQuantity(7);
        Hold hold = new Hold();
        hold.setId("hold-1");
        hold.setDrop(drop);
        hold.setCustomerId("customer-1");
        hold.setQuantity(3);
        hold.setStatus(HoldStatus.ACTIVE);
        hold.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));

        when(holdRepository.findByIdForUpdate("hold-1")).thenReturn(Optional.of(hold));
        when(dropRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(drop));
        when(holdRepository.save(any(Hold.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Hold cancelled = reservationService.cancelHold("hold-1");

        assertEquals(HoldStatus.CANCELLED, cancelled.getStatus());
        assertEquals(10, drop.getAvailableQuantity());
    }

    @Test
    void confirmExpiredHoldMarksItExpiredAndRestoresInventory() {
        drop.setAvailableQuantity(7);
        Hold hold = new Hold();
        hold.setId("hold-1");
        hold.setDrop(drop);
        hold.setCustomerId("customer-1");
        hold.setQuantity(3);
        hold.setStatus(HoldStatus.ACTIVE);
        hold.setExpiresAt(Instant.now().minus(1, ChronoUnit.SECONDS));

        when(holdRepository.findByIdForUpdate("hold-1")).thenReturn(Optional.of(hold));
        when(dropRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(drop));
        when(holdRepository.save(any(Hold.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Hold expired = reservationService.confirmHold("hold-1");

        assertEquals(HoldStatus.EXPIRED, expired.getStatus());
        assertEquals(10, drop.getAvailableQuantity());
    }

    @Test
    void cannotCancelAlreadyConfirmedHold() {
        Hold hold = new Hold();
        hold.setId("hold-1");
        hold.setDrop(drop);
        hold.setStatus(HoldStatus.CONFIRMED);
        when(holdRepository.findByIdForUpdate("hold-1")).thenReturn(Optional.of(hold));

        assertThrows(ReservationConflictException.class,
                () -> reservationService.cancelHold("hold-1"));

        verify(dropRepository, never()).findByIdForUpdate(any());
    }
}
