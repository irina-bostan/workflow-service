package com.aniri.workflow_service.domain.booking.exception;

import java.util.UUID;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(UUID bookingId) {
        super("Booking not found: " + bookingId);
    }
}
