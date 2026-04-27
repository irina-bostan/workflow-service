package com.aniri.workflow_service.application.duffel;

import com.aniri.workflow_service.domain.booking.exception.BookingProviderRejectionException;
import com.aniri.workflow_service.domain.booking.model.BookingCreatedEvent;
import com.aniri.workflow_service.domain.booking.model.ResourceType;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MockBookingProviderTest {

    private final MockBookingProvider provider = new MockBookingProvider();

    private static BookingCreatedEvent event() {
        return BookingCreatedEvent.builder()
                .bookingId(UUID.randomUUID())
                .employeeId("EMP9876")
                .resourceType(ResourceType.FLIGHT)
                .destination("NYC")
                .departureDate(OffsetDateTime.parse("2027-11-05T08:00:00Z"))
                .build();
    }

    @RepeatedTest(50)
    void reserve_returnsRefOrRejects() {
        try {
            final String ref = provider.reserve(event());
            assertThat(ref).startsWith("DUFFEL-MOCK-").hasSize("DUFFEL-MOCK-".length() + 8);
        } catch (final BookingProviderRejectionException e) {
            assertThat(e.getMessage()).contains("Provider rejected");
        }
    }

    @Test
    void reserve_referencesAreUniquePerCall() {
        // Run a handful of successful calls; refs should not collide. With a 5% reject rate
        // and 20 attempts, we have a >>99% chance of at least 10 successes — enough to assert
        // uniqueness without making the test flaky on the rejection path.
        final var refs = new java.util.HashSet<String>();
        int successes = 0;
        for (int i = 0; i < 20 && successes < 10; i++) {
            try {
                refs.add(provider.reserve(event()));
                successes++;
            } catch (final BookingProviderRejectionException ignored) {
                // simulated; keep iterating
            }
        }
        assertThat(refs).hasSize(successes);
    }
}
