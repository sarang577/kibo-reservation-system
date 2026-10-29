package com.kibo.reservation.exception;

public class DropNotFoundException extends RuntimeException {
    public DropNotFoundException(Long id) {
        super("Drop not found: " + id);
    }
}
