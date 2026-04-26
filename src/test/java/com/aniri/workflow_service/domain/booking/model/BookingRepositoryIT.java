package com.aniri.workflow_service.domain.booking.model;

import com.aniri.workflow_service.domain.employee.model.EmployeeEntity;
import com.aniri.workflow_service.domain.employee.model.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class BookingRepositoryIT {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private EmployeeRepository employeeRepository;

    @Test
    void findByIdempotencyKey_existingKey_returnsBooking() {
        employeeRepository.save(EmployeeEntity.builder()
                .employeeId("EMP9876")
                .firstName("Alice").lastName("Smith")
                .email("alice@techquarter.com").department("Eng").build());

        final BookingEntity saved = bookingRepository.save(BookingEntity.builder()
                .employeeId("EMP9876")
                .resourceType(ResourceType.FLIGHT)
                .destination("NYC")
                .departureDate(OffsetDateTime.parse("2027-11-05T08:00:00Z"))
                .travelerCount(1)
                .costCenterRef("CC-456")
                .idempotencyKey("idem-key-123")
                .build());

        final Optional<BookingEntity> found = bookingRepository.findByIdempotencyKey("idem-key-123");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findByIdempotencyKey_unknownKey_returnsEmpty() {
        final Optional<BookingEntity> found = bookingRepository.findByIdempotencyKey("does-not-exist");
        assertThat(found).isEmpty();
    }
}
