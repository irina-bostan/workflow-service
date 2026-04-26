package com.aniri.workflow_service.domain.booking;

import com.aniri.workflow_service.domain.booking.model.BookingCreatedEvent;

/**
 * Port for fan-out of booking domain events.
 * Adapter is selected by configuration: SQS in local/stage/prod, no-op in dev.
 */
public interface BookingEventPublisher {

    void publishBookingCreated(BookingCreatedEvent event);
}
