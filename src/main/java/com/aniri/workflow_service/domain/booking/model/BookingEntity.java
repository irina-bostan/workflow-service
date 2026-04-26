package com.aniri.workflow_service.domain.booking.model;

import com.aniri.workflow_service.domain.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingEntity extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "employee_id", nullable = false, length = 20)
    private String employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    private ResourceType resourceType;

    @Column(nullable = false, length = 50)
    private String destination;

    @Column(name = "departure_date", nullable = false)
    private OffsetDateTime departureDate;

    @Column(name = "return_date")
    private OffsetDateTime returnDate;

    @Column(name = "traveler_count", nullable = false)
    private int travelerCount;

    @Column(name = "cost_center_ref", nullable = false, length = 50)
    private String costCenterRef;

    @Column(name = "trip_purpose", length = 500)
    private String tripPurpose;

    @Column(name = "idempotency_key", unique = true, length = 255)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;
}
