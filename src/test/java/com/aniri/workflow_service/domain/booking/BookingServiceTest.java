package com.aniri.workflow_service.domain.booking;

import com.aniri.workflow_service.domain.booking.exception.BookingNotFoundException;
import com.aniri.workflow_service.domain.booking.model.BookingEntity;
import com.aniri.workflow_service.domain.booking.model.BookingMapper;
import com.aniri.workflow_service.domain.booking.model.BookingRepository;
import com.aniri.workflow_service.domain.booking.model.BookingStatus;
import com.aniri.workflow_service.domain.employee.exception.EmployeeNotFoundException;
// Domain enum used for entity setup; the test still uses the wire enum below for DTOs.
import static com.aniri.workflow_service.domain.booking.model.ResourceType.FLIGHT;
import static com.aniri.workflow_service.domain.booking.model.ResourceType.HOTEL;
import com.aniri.workflow_service.domain.employee.model.EmployeeEntity;
import com.aniri.workflow_service.domain.employee.model.EmployeeRepository;
import com.aniri.workflow_service.domain.outbox.OutboxEntry;
import com.aniri.workflow_service.domain.outbox.OutboxRepository;
import com.aniri.workflow_service.web.model.Booking;
import com.aniri.workflow_service.web.model.ResourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final String IDEMPOTENCY_KEY = "11111111-1111-1111-1111-111111111111";
    private static final String EMPLOYEE_ID = "EMP9876";

    @Mock private BookingRepository bookingRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private BookingMapper bookingMapper;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private BookingService bookingService;

    @Test
    void create_newRequest_savesBookingAndOutboxAndReturnsDto() throws Exception {
        final Booking inputDto = newWireBooking();
        final BookingEntity toSave = BookingEntity.builder().employeeId(EMPLOYEE_ID).build();
        final BookingEntity saved = BookingEntity.builder().id(UUID.randomUUID()).employeeId(EMPLOYEE_ID).build();
        final Booking returned = new Booking().employeeId(EMPLOYEE_ID);

        when(bookingRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(employeeRepository.findByEmployeeId(EMPLOYEE_ID))
                .thenReturn(Optional.of(EmployeeEntity.builder().employeeId(EMPLOYEE_ID).build()));
        when(bookingMapper.toEntity(inputDto, IDEMPOTENCY_KEY)).thenReturn(toSave);
        when(bookingRepository.save(toSave)).thenReturn(saved);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(bookingMapper.toDto(saved)).thenReturn(returned);

        final Booking result = bookingService.create(inputDto, IDEMPOTENCY_KEY);

        assertThat(result).isSameAs(returned);
        verify(bookingRepository).save(toSave);
        verify(outboxRepository).save(any(OutboxEntry.class));
    }

    @Test
    void create_existingIdempotencyKey_returnsExistingBookingWithoutSaving() {
        final BookingEntity existing = BookingEntity.builder().id(UUID.randomUUID()).build();
        final Booking returned = new Booking();

        when(bookingRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(existing));
        when(bookingMapper.toDto(existing)).thenReturn(returned);

        final Booking result = bookingService.create(newWireBooking(), IDEMPOTENCY_KEY);

        assertThat(result).isSameAs(returned);
        verify(employeeRepository, never()).findByEmployeeId(any());
        verify(bookingRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void create_employeeDoesNotExist_throwsEmployeeNotFoundException() {
        final Booking inputDto = newWireBooking();

        when(bookingRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(employeeRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.create(inputDto, IDEMPOTENCY_KEY))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining(EMPLOYEE_ID);

        verify(bookingRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void findById_existingBooking_returnsEntity() {
        final UUID bookingId = UUID.randomUUID();
        final BookingEntity entity = BookingEntity.builder().id(bookingId).build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(entity));

        assertThat(bookingService.findById(bookingId)).isSameAs(entity);
    }

    @Test
    void findById_unknownBooking_throwsBookingNotFoundException() {
        final UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.findById(bookingId))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining(bookingId.toString());
    }

    @Test
    void confirm_pendingBooking_marksConfirmed() {
        final UUID bookingId = UUID.randomUUID();
        final BookingEntity booking = BookingEntity.builder().id(bookingId).build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        bookingService.confirm(bookingId, "DUFFEL-MOCK-ABCD1234");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getProviderRef()).isEqualTo("DUFFEL-MOCK-ABCD1234");
    }

    @Test
    void confirm_alreadyConfirmed_isNoOp() {
        final UUID bookingId = UUID.randomUUID();
        final BookingEntity booking = BookingEntity.builder().id(bookingId).build();
        booking.markConfirmed("FIRST-REF");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        bookingService.confirm(bookingId, "SECOND-REF");

        assertThat(booking.getProviderRef()).isEqualTo("FIRST-REF");
    }

    @Test
    void cancelByUser_pendingFlight_marksCancelledWithCascade() {
        final UUID bookingId = UUID.randomUUID();
        final UUID tripId = UUID.randomUUID();
        final BookingEntity booking = BookingEntity.builder()
                .id(bookingId).tripId(tripId).resourceType(FLIGHT).build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByTripIdOrderByCreatedAtAsc(tripId))
                .thenReturn(java.util.List.of(booking));

        bookingService.cancelByUser(bookingId, "Plans changed");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancellationReason()).isEqualTo("Plans changed");
    }

    @Test
    void cancelByUser_flightCancellation_cascadesToHotelInSameTrip() {
        final UUID tripId = UUID.randomUUID();
        final UUID flightId = UUID.randomUUID();
        final UUID hotelId = UUID.randomUUID();
        final BookingEntity flight = BookingEntity.builder()
                .id(flightId).tripId(tripId).resourceType(FLIGHT).build();
        final BookingEntity hotel = BookingEntity.builder()
                .id(hotelId).tripId(tripId).resourceType(HOTEL).build();
        hotel.markConfirmed("DUFFEL-MOCK-OK");

        when(bookingRepository.findById(flightId)).thenReturn(Optional.of(flight));
        when(bookingRepository.findByTripIdOrderByCreatedAtAsc(tripId))
                .thenReturn(java.util.List.of(flight, hotel));

        bookingService.cancelByUser(flightId, "Plans changed");

        assertThat(flight.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(hotel.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(hotel.getCancellationReason()).isEqualTo("Plans changed");
        // providerRef preserved on the formerly-confirmed sibling so audit / upstream
        // cancel calls can still reach the original reservation.
        assertThat(hotel.getProviderRef()).isEqualTo("DUFFEL-MOCK-OK");
    }

    @Test
    void cancelByUser_hotelCancellation_doesNotCascadeToFlight() {
        // User changes mind on the hotel only; the flight stays active so they can still
        // make the trip (e.g. stay with friends instead).
        final UUID tripId = UUID.randomUUID();
        final UUID flightId = UUID.randomUUID();
        final UUID hotelId = UUID.randomUUID();
        final BookingEntity flight = BookingEntity.builder()
                .id(flightId).tripId(tripId).resourceType(FLIGHT).build();
        flight.markConfirmed("DUFFEL-MOCK-FLT");
        final BookingEntity hotel = BookingEntity.builder()
                .id(hotelId).tripId(tripId).resourceType(HOTEL).build();

        when(bookingRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        bookingService.cancelByUser(hotelId, "Staying with friends");

        assertThat(hotel.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(flight.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(flight.getProviderRef()).isEqualTo("DUFFEL-MOCK-FLT");
        // Hotel-only cancel must NOT have looked up siblings.
        verify(bookingRepository, never()).findByTripIdOrderByCreatedAtAsc(any());
    }

    @Test
    void cancelByUser_skipsAlreadyCancelledSiblings() {
        final UUID tripId = UUID.randomUUID();
        final UUID flightId = UUID.randomUUID();
        final BookingEntity flight = BookingEntity.builder()
                .id(flightId).tripId(tripId).resourceType(FLIGHT).build();
        final BookingEntity already = BookingEntity.builder()
                .id(UUID.randomUUID()).tripId(tripId).resourceType(HOTEL).build();
        already.markCancelled("first reason");

        when(bookingRepository.findById(flightId)).thenReturn(Optional.of(flight));
        when(bookingRepository.findByTripIdOrderByCreatedAtAsc(tripId))
                .thenReturn(java.util.List.of(flight, already));

        bookingService.cancelByUser(flightId, "Plans changed");

        assertThat(flight.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        // Sibling's reason untouched — cascade doesn't overwrite a prior cancel.
        assertThat(already.getCancellationReason()).isEqualTo("first reason");
    }

    @Test
    void cancelByUser_confirmedFlight_marksCancelled() {
        final UUID bookingId = UUID.randomUUID();
        final UUID tripId = UUID.randomUUID();
        final BookingEntity booking = BookingEntity.builder()
                .id(bookingId).tripId(tripId).resourceType(FLIGHT).build();
        booking.markConfirmed("DUFFEL-MOCK-ABCD1234");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByTripIdOrderByCreatedAtAsc(tripId))
                .thenReturn(java.util.List.of(booking));

        bookingService.cancelByUser(bookingId, "Plans changed");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancellationReason()).isEqualTo("Plans changed");
    }

    @Test
    void cancelByUser_alreadyCancelled_isNoOp() {
        final UUID bookingId = UUID.randomUUID();
        final UUID tripId = UUID.randomUUID();
        final BookingEntity booking = BookingEntity.builder()
                .id(bookingId).tripId(tripId).resourceType(FLIGHT).build();
        booking.markCancelled("first reason");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByTripIdOrderByCreatedAtAsc(tripId))
                .thenReturn(java.util.List.of(booking));

        bookingService.cancelByUser(bookingId, "second reason");

        assertThat(booking.getCancellationReason()).isEqualTo("first reason");
    }

    @Test
    void cancel_pendingBooking_marksCancelled() {
        final UUID bookingId = UUID.randomUUID();
        final BookingEntity booking = BookingEntity.builder().id(bookingId).build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        bookingService.cancel(bookingId, "no availability");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancellationReason()).isEqualTo("no availability");
    }

    private static Booking newWireBooking() {
        return new Booking()
                .employeeId(EMPLOYEE_ID)
                .resourceType(ResourceType.FLIGHT)
                .destination("NYC")
                .departureDate(OffsetDateTime.parse("2027-11-05T08:00:00Z"))
                .travelerCount(1)
                .costCenterRef("CC-456");
    }
}
