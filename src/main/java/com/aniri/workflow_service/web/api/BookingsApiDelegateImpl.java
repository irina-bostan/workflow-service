package com.aniri.workflow_service.web.api;

import com.aniri.workflow_service.domain.appointment.AppointmentService;
import com.aniri.workflow_service.domain.booking.BookingService;
import com.aniri.workflow_service.domain.booking.exception.InvalidBookingRequestException;
import com.aniri.workflow_service.domain.booking.model.BookingEntity;
import com.aniri.workflow_service.domain.booking.model.BookingMapper;
import com.aniri.workflow_service.domain.persistence.AuditableEntity;
import com.aniri.workflow_service.domain.search.SearchService;
import com.aniri.workflow_service.domain.search.model.SearchRequest;
import com.aniri.workflow_service.web.model.Appointment;
import com.aniri.workflow_service.web.model.Booking;
import com.aniri.workflow_service.web.model.BookingSearch;
import com.aniri.workflow_service.web.model.PagedBookingResponse;
import com.aniri.workflow_service.web.model.ResourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
public class BookingsApiDelegateImpl implements BookingsApiDelegate {

    private final BookingService bookingService;
    private final SearchService searchService;
    private final AppointmentService appointmentService;
    private final BookingMapper bookingMapper;

    @Override
    public ResponseEntity<Booking> createBooking(final UUID idempotencyKey, final Booking booking) {
        validateBooking(booking);

        final Booking createdBooking = bookingService.create(booking, idempotencyKey.toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(createdBooking);
    }

    @Override
    public ResponseEntity<PagedBookingResponse> listBookings(final String employeeId,
                                                             final Integer page,
                                                             final Integer size) {
        final PageRequest pageable = PageRequest.of(page, size, Sort.by(AuditableEntity.Fields.createdAt).descending());

        final Page<BookingEntity> bookingPage = bookingService.findByEmployeeId(employeeId, pageable);
        final List<Booking> content = bookingPage.getContent().stream()
                .map(bookingMapper::toDto)
                .toList();

        final PagedBookingResponse bookings = new PagedBookingResponse()
                .page(bookingPage.getNumber())
                .size(bookingPage.getSize())
                .totalElements(bookingPage.getTotalElements())
                .totalPages(bookingPage.getTotalPages())
                .bookings(content);
        return ResponseEntity.ok(bookings);
    }

    @Override
    public ResponseEntity<List<BookingSearch>> searchBookingOptions(final ResourceType resourceType,
                                                                    final String destination,
                                                                    final OffsetDateTime departureDate,
                                                                    final OffsetDateTime returnDate,
                                                                    final Integer travelerCount) {
        final SearchRequest request = SearchRequest.builder()
                .resourceType(com.aniri.workflow_service.domain.booking.model.ResourceType.valueOf(resourceType.name()))
                .destination(destination)
                .departureDate(departureDate)
                .returnDate(returnDate)
                .travelerCount(travelerCount)
                .build();
        final List<BookingSearch> searchResult = searchService.search(request);
        return ResponseEntity.ok(searchResult);
    }

    @Override
    public ResponseEntity<Appointment> createAppointment(final UUID bookingId, final Appointment appointment) {
        appointment.setBookingId(bookingId);

        final Appointment createdAppointment = appointmentService.create(appointment);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAppointment);
    }

    private static void validateBooking(final Booking booking) {
        if (booking.getResourceType() == ResourceType.HOTEL && isNull(booking.getReturnDate())) {
            throw new InvalidBookingRequestException("Hotel bookings require a returnDate");
        }
    }
}
