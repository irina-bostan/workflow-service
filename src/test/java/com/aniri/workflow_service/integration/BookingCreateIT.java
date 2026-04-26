package com.aniri.workflow_service.integration;

import com.aniri.workflow_service.domain.booking.model.BookingRepository;
import com.aniri.workflow_service.domain.employee.model.EmployeeEntity;
import com.aniri.workflow_service.domain.employee.model.EmployeeRepository;
import com.aniri.workflow_service.domain.outbox.OutboxRepository;
import com.aniri.workflow_service.domain.outbox.OutboxStatus;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BookingCreateIT {

    private static final String EMPLOYEE_ID = "EMP9876";

    @LocalServerPort int port;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired OutboxRepository outboxRepository;

    @BeforeEach
    void setup() {
        RestAssured.port = port;
        outboxRepository.deleteAll();
        bookingRepository.deleteAll();
        employeeRepository.deleteAll();
        employeeRepository.save(EmployeeEntity.builder()
                .employeeId(EMPLOYEE_ID)
                .firstName("Alice").lastName("Smith")
                .email("alice@techquarter.com").department("Engineering").build());
    }

    @Test
    void createBooking_validFlightRequest_persistsBookingAndOutbox() {
        final UUID idemKey = UUID.randomUUID();
        given()
                .header("Idempotency-Key", idemKey.toString())
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "employeeId": "EMP9876",
                          "resourceType": "FLIGHT",
                          "destination": "NYC",
                          "departureDate": "2027-11-05T08:00:00Z",
                          "returnDate": "2027-11-08T18:00:00Z",
                          "travelerCount": 1,
                          "costCenterRef": "CC-456",
                          "tripPurpose": "Client meeting"
                        }
                        """)
                .when().post("/bookings")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("employeeId", equalTo("EMP9876"))
                .body("destination", equalTo("NYC"));

        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.findAll().getFirst().getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void createBooking_idempotentReplay_returnsSameBookingWithoutDuplication() {
        final String idemKey = UUID.randomUUID().toString();
        final String body = """
                {
                  "employeeId": "EMP9876",
                  "resourceType": "FLIGHT",
                  "destination": "NYC",
                  "departureDate": "2027-11-05T08:00:00Z",
                  "returnDate": "2027-11-08T18:00:00Z",
                  "travelerCount": 1,
                  "costCenterRef": "CC-456"
                }
                """;

        final String firstId = given().header("Idempotency-Key", idemKey).contentType(ContentType.JSON).body(body)
                .when().post("/bookings")
                .then().statusCode(201).extract().path("id");

        final String secondId = given().header("Idempotency-Key", idemKey).contentType(ContentType.JSON).body(body)
                .when().post("/bookings")
                .then().statusCode(201).extract().path("id");

        assertThat(secondId).isEqualTo(firstId);
        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void createBooking_unknownEmployee_returns404() {
        given()
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "employeeId": "DOES-NOT-EXIST",
                          "resourceType": "FLIGHT",
                          "destination": "NYC",
                          "departureDate": "2027-11-05T08:00:00Z",
                          "travelerCount": 1,
                          "costCenterRef": "CC-456"
                        }
                        """)
                .when().post("/bookings")
                .then()
                .statusCode(404)
                .body("reasonCode", equalTo("EMPLOYEE_NOT_FOUND"));
    }
}
