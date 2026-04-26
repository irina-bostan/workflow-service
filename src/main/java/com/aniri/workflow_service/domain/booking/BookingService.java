package com.aniri.workflow_service.domain.booking;

import com.aniri.workflow_service.domain.booking.model.BookingCreatedEvent;
import com.aniri.workflow_service.domain.booking.model.BookingEntity;
import com.aniri.workflow_service.domain.booking.model.BookingMapper;
import com.aniri.workflow_service.domain.booking.model.BookingRepository;
import com.aniri.workflow_service.domain.employee.exception.EmployeeNotFoundException;
import com.aniri.workflow_service.domain.employee.model.EmployeeRepository;
import com.aniri.workflow_service.domain.outbox.OutboxEntry;
import com.aniri.workflow_service.domain.outbox.OutboxRepository;
import com.aniri.workflow_service.web.model.Booking;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private static final String AGGREGATE_TYPE = "BOOKING";
    private static final String EVENT_TYPE_BOOKING_CREATED = "BookingCreatedEvent";

    private final BookingRepository bookingRepository;
    private final EmployeeRepository employeeRepository;
    private final OutboxRepository outboxRepository;
    private final BookingMapper bookingMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public Booking create(final Booking dto, final String idempotencyKey) {
        final Optional<BookingEntity> existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent replay key={} → bookingId={}", idempotencyKey, existing.get().getId());
            return bookingMapper.toDto(existing.get());
        }

        ensureEmployeeExists(dto.getEmployeeId());

        final BookingEntity saved = bookingRepository.save(bookingMapper.toEntity(dto, idempotencyKey));
        outboxRepository.save(buildOutboxEntry(saved));

        log.info("Created booking id={} employeeId={}", saved.getId(), saved.getEmployeeId());
        return bookingMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<BookingEntity> findByEmployeeId(final String employeeId, final Pageable pageable) {
        return bookingRepository.findByEmployeeId(employeeId, pageable);
    }

    private void ensureEmployeeExists(final String employeeId) {
        employeeRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }

    private OutboxEntry buildOutboxEntry(final BookingEntity saved) {
        final BookingCreatedEvent event = BookingCreatedEvent.builder()
                .bookingId(saved.getId())
                .employeeId(saved.getEmployeeId())
                .resourceType(saved.getResourceType())
                .destination(saved.getDestination())
                .departureDate(saved.getDepartureDate())
                .build();
        try {
            return OutboxEntry.builder()
                    .aggregateType(AGGREGATE_TYPE)
                    .aggregateId(saved.getId())
                    .eventType(EVENT_TYPE_BOOKING_CREATED)
                    .payload(objectMapper.writeValueAsString(event))
                    .build();
        } catch (final JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize BookingCreatedEvent", e);
        }
    }
}
