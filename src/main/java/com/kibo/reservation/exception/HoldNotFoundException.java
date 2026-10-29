package com.kibo.reservation.exception;

public class HoldNotFoundException extends RuntimeException {
    public HoldNotFoundException(String id) {
        super("Hold not found: " + id);
    }
}
