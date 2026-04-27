package com.aniri.workflow_service.domain.booking.model;

import com.aniri.workflow_service.web.model.Booking;
import com.aniri.workflow_service.web.model.ResourceType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface BookingMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true) // server-controlled; default PENDING set by entity
    @Mapping(target = "tripId", ignore = true)
        // server-controlled; set explicitly in BookingService.create
    BookingEntity toEntity(Booking dto, String idempotencyKey);

    Booking toDto(BookingEntity entity);

    com.aniri.workflow_service.domain.booking.model.ResourceType toDomainResourceType(ResourceType wire);

    ResourceType toWireResourceType(com.aniri.workflow_service.domain.booking.model.ResourceType domain);

    com.aniri.workflow_service.web.model.BookingStatus toWireStatus(BookingStatus domain);

    BookingStatus toDomainStatus(com.aniri.workflow_service.web.model.BookingStatus wire);
}
