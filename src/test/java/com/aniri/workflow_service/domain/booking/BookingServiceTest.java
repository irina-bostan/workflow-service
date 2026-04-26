package com.aniri.workflow_service.domain.booking;

import com.aniri.workflow_service.domain.booking.model.BookingEntity;
import com.aniri.workflow_service.domain.booking.model.BookingMapper;
import com.aniri.workflow_service.domain.booking.model.BookingRepository;
import com.aniri.workflow_service.domain.employee.exception.EmployeeNotFoundException;
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
