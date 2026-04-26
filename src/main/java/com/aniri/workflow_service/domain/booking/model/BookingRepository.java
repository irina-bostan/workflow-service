package com.aniri.workflow_service.domain.booking.model;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<BookingEntity, UUID> {

    Page<BookingEntity> findByEmployeeId(String employeeId, Pageable pageable);

    Optional<BookingEntity> findByIdempotencyKey(String idempotencyKey);
}
