package com.aniri.workflow_service.domain.appointment.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<AppointmentEntity, UUID> {

    List<AppointmentEntity> findByBookingIdOrderByScheduledAtAsc(UUID bookingId);
}
