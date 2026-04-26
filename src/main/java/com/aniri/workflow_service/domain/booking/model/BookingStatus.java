package com.aniri.workflow_service.domain.booking.model;

/**
 * Lifecycle of a booking from request to confirmation.
 *
 * <ul>
 *   <li>{@link #PENDING} — DB record exists; the provider (Duffel/hotel chain) hasn't acknowledged
 *       the reservation yet. Default at insert.</li>
 *   <li>{@link #CONFIRMED} — provider acknowledged. Set by the downstream consumer of
 *       {@code BookingCreatedEvent} once the external booking returns success.</li>
 *   <li>{@link #CANCELLED} — explicitly cancelled by the user or rejected by the provider.</li>
 * </ul>
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
}
