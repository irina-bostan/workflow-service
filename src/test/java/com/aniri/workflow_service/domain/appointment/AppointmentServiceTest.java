package com.aniri.workflow_service.domain.appointment;

import com.aniri.workflow_service.domain.appointment.model.AppointmentEntity;
import com.aniri.workflow_service.domain.appointment.model.AppointmentMapper;
import com.aniri.workflow_service.domain.appointment.model.AppointmentRepository;
import com.aniri.workflow_service.domain.booking.exception.BookingNotFoundException;
import com.aniri.workflow_service.domain.booking.exception.InvalidBookingRequestException;
import com.aniri.workflow_service.domain.booking.model.BookingEntity;
import com.aniri.workflow_service.domain.booking.model.BookingRepository;
import com.aniri.workflow_service.domain.booking.model.ResourceType;
import com.aniri.workflow_service.web.model.Appointment;
import com.aniri.workflow_service.web.model.AppointmentType;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private AppointmentMapper appointmentMapper;

    @InjectMocks private AppointmentService appointmentService;

    @Test
    void create_validHotelBooking_savesAndReturnsDto() {
        final UUID bookingId = UUID.randomUUID();
        final Appointment dto = newAppointmentDto(bookingId);
        final BookingEntity hotelBooking = BookingEntity.builder().id(bookingId).resourceType(ResourceType.HOTEL).build();
        final AppointmentEntity toSave = AppointmentEntity.builder().booking(hotelBooking).build();
        final AppointmentEntity saved = AppointmentEntity.builder().id(UUID.randomUUID()).booking(hotelBooking).build();
        final Appointment returned = new Appointment();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(hotelBooking));
        when(appointmentMapper.toEntity(dto, hotelBooking)).thenReturn(toSave);
        when(appointmentRepository.save(toSave)).thenReturn(saved);
        when(appointmentMapper.toDto(saved)).thenReturn(returned);

        final Appointment result = appointmentService.create(dto);

        assertThat(result).isSameAs(returned);
        verify(appointmentRepository).save(toSave);
    }

    @Test
    void create_bookingNotFound_throwsBookingNotFoundException() {
        final UUID bookingId = UUID.randomUUID();
        final Appointment dto = newAppointmentDto(bookingId);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.create(dto))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining(bookingId.toString());

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void create_bookingIsNotHotel_throwsInvalidBookingRequestException() {
        final UUID bookingId = UUID.randomUUID();
        final Appointment dto = newAppointmentDto(bookingId);
        final BookingEntity flightBooking = BookingEntity.builder().id(bookingId).resourceType(ResourceType.FLIGHT).build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(flightBooking));

        assertThatThrownBy(() -> appointmentService.create(dto))
                .isInstanceOf(InvalidBookingRequestException.class)
                .hasMessageContaining("hotel");

        verify(appointmentRepository, never()).save(any());
    }

    private static Appointment newAppointmentDto(final UUID bookingId) {
        return new Appointment()
                .bookingId(bookingId)
                .appointmentType(AppointmentType.SPA)
                .scheduledAt(OffsetDateTime.parse("2027-11-06T10:00:00Z"));
    }
}
