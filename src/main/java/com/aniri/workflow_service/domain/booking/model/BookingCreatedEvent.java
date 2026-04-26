package com.aniri.workflow_service.domain.booking.model;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record BookingCreatedEvent(
        UUID bookingId,
        String employeeId,
        ResourceType resourceType,
        String destination,
        OffsetDateTime departureDate
) {
}
