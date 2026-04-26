package com.aniri.workflow_service.domain.search.model;

import com.aniri.workflow_service.domain.booking.model.ResourceType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Builder
public record SearchResult(
        String providerId,
        ResourceType resourceType,
        String origin,
        String destination,
        OffsetDateTime departureTime,
        OffsetDateTime arrivalTime,
        int availableSeats,
        BigDecimal pricePerPerson,
        String currency,
        String providerName
) {
}
