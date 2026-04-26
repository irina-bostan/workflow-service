package com.aniri.workflow_service.domain.appointment.model;

import com.aniri.workflow_service.domain.booking.model.BookingEntity;
import com.aniri.workflow_service.web.model.Appointment;
import com.aniri.workflow_service.web.model.AppointmentType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface AppointmentMapper {

    @Mapping(target = "id", ignore = true)
    AppointmentEntity toEntity(Appointment dto, BookingEntity booking);

    @Mapping(target = "bookingId", source = "entity.booking.id")
    Appointment toDto(AppointmentEntity entity);

    com.aniri.workflow_service.domain.appointment.model.AppointmentType toDomainType(AppointmentType wire);

    AppointmentType toWireType(com.aniri.workflow_service.domain.appointment.model.AppointmentType domain);
}
