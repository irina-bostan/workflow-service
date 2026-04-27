package com.aniri.workflow_service.domain.booking;

import com.aniri.workflow_service.domain.booking.exception.BookingProviderRejectionException;
import com.aniri.workflow_service.domain.booking.model.BookingCreatedEvent;

/**
 * Abstraction over an upstream travel-inventory provider (Duffel in prod, mock in dev/test).
 * The SQS consumer invokes this after the booking is persisted; the returned reference is
 * stored on the booking row when the upstream confirms the reservation.
 */
public interface BookingProvider {

    /**
     * Reserve the booking with the upstream provider.
     *
     * @return the provider's reservation reference (e.g. Duffel order id)
     * @throws BookingProviderRejectionException if the provider rejects the request terminally
     */
    String reserve(BookingCreatedEvent event);
}
