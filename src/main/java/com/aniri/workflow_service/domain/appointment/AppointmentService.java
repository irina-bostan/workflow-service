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
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final BookingRepository bookingRepository;
    private final AppointmentMapper appointmentMapper;

    @Transactional(readOnly = true)
    public java.util.List<Appointment> findByBookingId(final java.util.UUID bookingId) {
        if (!bookingRepository.existsById(bookingId)) {
            throw new BookingNotFoundException(bookingId);
        }
        return appointmentRepository.findByBookingIdOrderByScheduledAtAsc(bookingId).stream()
                .map(appointmentMapper::toDto)
                .toList();
    }

    @Transactional
    @Observed(name = "appointment.create", contextualName = "AppointmentService#create")
    public Appointment create(final Appointment appointment) {
        final BookingEntity booking = bookingRepository.findById(appointment.getBookingId())
                .orElseThrow(() -> new BookingNotFoundException(appointment.getBookingId()));

        validateBooking(appointment, booking);
        final AppointmentEntity saved = appointmentRepository.save(appointmentMapper.toEntity(appointment, booking));

        log.info("Created appointment id={} bookingId={}", saved.getId(), booking.getId());
        return appointmentMapper.toDto(saved);
    }

    private static void validateBooking(final Appointment dto, final BookingEntity booking) {
        if (booking.getResourceType() != ResourceType.HOTEL) {
            throw new InvalidBookingRequestException(
                    "Appointments can only be created for hotel bookings, but booking "
                            + dto.getBookingId() + " is a " + booking.getResourceType());
        }
    }
}
