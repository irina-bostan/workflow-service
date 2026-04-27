package com.aniri.workflow_service.domain.booking;

import com.aniri.workflow_service.domain.booking.exception.BookingNotFoundException;
import com.aniri.workflow_service.domain.booking.exception.InvalidBookingRequestException;
import com.aniri.workflow_service.domain.booking.model.BookingCreatedEvent;
import com.aniri.workflow_service.domain.booking.model.BookingEntity;
import com.aniri.workflow_service.domain.booking.model.BookingMapper;
import com.aniri.workflow_service.domain.booking.model.BookingRepository;
import com.aniri.workflow_service.domain.booking.model.BookingStatus;
import com.aniri.workflow_service.domain.booking.model.ResourceType;
import com.aniri.workflow_service.domain.employee.exception.EmployeeNotFoundException;
import com.aniri.workflow_service.domain.employee.model.EmployeeRepository;
import com.aniri.workflow_service.domain.outbox.OutboxEntry;
import com.aniri.workflow_service.domain.outbox.OutboxRepository;
import com.aniri.workflow_service.web.model.Booking;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    @Observed(name = "booking.create", contextualName = "BookingService#create")
    public Booking create(final Booking dto, final String idempotencyKey) {
        final Optional<BookingEntity> existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent replay key={} → bookingId={}", idempotencyKey, existing.get().getId());
            return bookingMapper.toDto(existing.get());
        }

        ensureEmployeeExists(dto.getEmployeeId());

        final BookingEntity entity = bookingMapper.toEntity(dto, idempotencyKey);
        entity.setTripId(resolveTripId(dto.getTripId(), dto.getEmployeeId()));

        final BookingEntity saved = bookingRepository.save(entity);
        outboxRepository.save(buildOutboxEntry(saved));

        log.info("Created booking id={} tripId={} employeeId={}",
                saved.getId(), saved.getTripId(), saved.getEmployeeId());
        return bookingMapper.toDto(saved);
    }

    /**
     * Returns the tripId to use for a new booking. If the caller supplied one, validates
     * that any existing bookings under that tripId belong to the same employee — without
     * this check a request could attach a booking to (and later cascade-cancel) someone
     * else's trip. If no tripId is supplied, generates a fresh one so each standalone
     * booking is its own trip-of-one.
     */
    private UUID resolveTripId(final UUID requested, final String employeeId) {
        if (requested == null) {
            return UUID.randomUUID();
        }
        final List<BookingEntity> existing = bookingRepository.findByTripIdOrderByCreatedAtAsc(requested);
        if (!existing.isEmpty() && !existing.getFirst().getEmployeeId().equals(employeeId)) {
            throw new InvalidBookingRequestException(
                    "Trip " + requested + " belongs to another employee");
        }
        return requested;
    }

    @Transactional(readOnly = true)
    public List<BookingEntity> findByTripId(final UUID tripId) {
        return bookingRepository.findByTripIdOrderByCreatedAtAsc(tripId);
    }

    @Transactional(readOnly = true)
    public Page<BookingEntity> findByEmployeeId(final String employeeId, final Pageable pageable) {
        return bookingRepository.findByEmployeeId(employeeId, pageable);
    }

    @Transactional(readOnly = true)
    public BookingEntity findById(final UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    /**
     * Idempotent: if the booking is already CONFIRMED or CANCELLED (e.g. SQS redelivery),
     * the call logs and returns without state change instead of throwing.
     */
    @Transactional
    @Observed(name = "booking.confirm", contextualName = "BookingService#confirm")
    public void confirm(final UUID bookingId, final String providerRef) {
        final BookingEntity booking = findById(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            log.info("Skipping confirm for booking={} already in status={}", bookingId, booking.getStatus());
            return;
        }
        booking.markConfirmed(providerRef);
        log.info("Confirmed booking id={} providerRef={}", bookingId, providerRef);
    }

    @Transactional
    @Observed(name = "booking.cancel", contextualName = "BookingService#cancel")
    public void cancel(final UUID bookingId, final String reason) {
        final BookingEntity booking = findById(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            log.info("Skipping cancel for booking={} already in status={}", bookingId, booking.getStatus());
            return;
        }
        booking.markCancelled(reason);
        log.info("Cancelled booking id={} reason={}", bookingId, reason);
    }

    /**
     * User-initiated cancel. Cascade rule reflects the product semantics of a trip:
     * <ul>
     *   <li><b>FLIGHT</b> — anchor of the trip. Cancelling it invalidates the entire
     *       trip, so every sibling booking sharing the tripId (still PENDING/CONFIRMED)
     *       is cancelled in the same transaction.</li>
     *   <li><b>HOTEL</b> (or any non-flight resource) — supplemental. Cancelling it
     *       does NOT cascade; the flight and other bookings stay active. Use case:
     *       user changes mind on the hotel but keeps the trip.</li>
     * </ul>
     * Idempotent: bookings already CANCELLED are skipped. A real provider integration
     * would also issue per-booking upstream order-cancels here.
     *
     * @return the booking the caller asked about, after the cancel(s) have run.
     */
    @Transactional
    @Observed(name = "booking.cancel.user", contextualName = "BookingService#cancelByUser")
    public BookingEntity cancelByUser(final UUID bookingId, final String reason) {
        final BookingEntity requested = findById(bookingId);
        final List<BookingEntity> toCancel = requested.getResourceType() == ResourceType.FLIGHT
                ? bookingRepository.findByTripIdOrderByCreatedAtAsc(requested.getTripId())
                : List.of(requested);
        int cancelled = 0;
        for (final BookingEntity booking : toCancel) {
            if (booking.getStatus() != BookingStatus.CANCELLED) {
                booking.markCancelled(reason);
                cancelled++;
            }
        }
        log.info("User cancelled originating={} type={} tripId={} cascade={} affected={}",
                bookingId, requested.getResourceType(), requested.getTripId(),
                requested.getResourceType() == ResourceType.FLIGHT, cancelled);
        return requested;
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
