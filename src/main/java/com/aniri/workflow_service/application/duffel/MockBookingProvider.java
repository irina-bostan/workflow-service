package com.aniri.workflow_service.application.duffel;

import com.aniri.workflow_service.domain.booking.BookingProvider;
import com.aniri.workflow_service.domain.booking.exception.BookingProviderRejectionException;
import com.aniri.workflow_service.domain.booking.model.BookingCreatedEvent;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Deterministic-ish mock of the upstream provider for the booking workflow.
 * Real Duffel order calls take seconds and cost money; the assessment exercises
 * the workflow shape (PENDING → CONFIRMED|CANCELLED), not the integration depth.
 * Returns a synthetic reference {@code DUFFEL-MOCK-XXXXXXXX} on success and throws
 * on a small percentage of requests so both UI paths render in demo runs.
 */
@Service
@Slf4j
public class MockBookingProvider implements BookingProvider {

    private static final double REJECTION_RATE = 0.05;
    private static final int REF_SUFFIX_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    @Override
    @Observed(name = "booking.provider.reserve", contextualName = "MockBookingProvider#reserve")
    public String reserve(final BookingCreatedEvent event) {
        if (random.nextDouble() < REJECTION_RATE) {
            log.info("Mock provider rejected booking={} (simulated)", event.bookingId());
            throw new BookingProviderRejectionException(
                    "Provider rejected: no availability at requested fare");
        }
        final String ref = "DUFFEL-MOCK-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, REF_SUFFIX_LENGTH).toUpperCase();
        log.info("Mock provider confirmed booking={} ref={}", event.bookingId(), ref);
        return ref;
    }
}
