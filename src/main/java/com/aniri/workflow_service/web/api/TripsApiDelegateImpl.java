package com.aniri.workflow_service.web.api;

import com.aniri.workflow_service.domain.booking.BookingService;
import com.aniri.workflow_service.domain.booking.model.BookingMapper;
import com.aniri.workflow_service.web.model.Booking;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripsApiDelegateImpl implements TripsApiDelegate {

    private final BookingService bookingService;
    private final BookingMapper bookingMapper;

    @Override
    public ResponseEntity<List<Booking>> listTripBookings(final UUID tripId) {
        final List<Booking> trip = bookingService.findByTripId(tripId).stream()
                .map(bookingMapper::toDto)
                .toList();
        return ResponseEntity.ok(trip);
    }
}
