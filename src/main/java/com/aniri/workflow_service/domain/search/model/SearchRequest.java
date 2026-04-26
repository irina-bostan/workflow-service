package com.aniri.workflow_service.domain.search.model;

import com.aniri.workflow_service.domain.booking.model.ResourceType;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record SearchRequest(
        ResourceType resourceType,
        String destination,
        OffsetDateTime departureDate,
        OffsetDateTime returnDate,
        int travelerCount
) {
}
