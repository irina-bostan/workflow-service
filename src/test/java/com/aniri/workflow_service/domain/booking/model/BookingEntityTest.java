package com.aniri.workflow_service.domain.booking.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingEntityTest {

    @Test
    void markConfirmed_pendingBooking_setsStatusAndProviderRef() {
        final BookingEntity booking = BookingEntity.builder().id(UUID.randomUUID()).build();

        booking.markConfirmed("DUFFEL-ORD-7H8K2");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getProviderRef()).isEqualTo("DUFFEL-ORD-7H8K2");
    }

    @Test
    void markCancelled_pendingBooking_setsStatusAndReason() {
        final BookingEntity booking = BookingEntity.builder().id(UUID.randomUUID()).build();

        booking.markCancelled("provider rejected");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancellationReason()).isEqualTo("provider rejected");
    }

    @Test
    void markConfirmed_alreadyConfirmed_throws() {
        final BookingEntity booking = BookingEntity.builder().id(UUID.randomUUID()).build();
        booking.markConfirmed("first-ref");

        assertThatThrownBy(() -> booking.markConfirmed("second-ref"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    void markCancelled_alreadyCancelled_throws() {
        final BookingEntity booking = BookingEntity.builder().id(UUID.randomUUID()).build();
        booking.markCancelled("first reason");

        assertThatThrownBy(() -> booking.markCancelled("second reason"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    void markConfirmed_afterCancelled_throws() {
        final BookingEntity booking = BookingEntity.builder().id(UUID.randomUUID()).build();
        booking.markCancelled("rejected");

        assertThatThrownBy(() -> booking.markConfirmed("late-ref"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markCancelled_confirmedBooking_transitionsToCancelled() {
        final BookingEntity booking = BookingEntity.builder().id(UUID.randomUUID()).build();
        booking.markConfirmed("DUFFEL-ORD-7H8K2");

        booking.markCancelled("Cancelled by user");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancellationReason()).isEqualTo("Cancelled by user");
        // providerRef preserved — useful for audit / upstream cancel calls.
        assertThat(booking.getProviderRef()).isEqualTo("DUFFEL-ORD-7H8K2");
    }
}
