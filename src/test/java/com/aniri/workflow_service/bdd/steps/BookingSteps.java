package com.aniri.workflow_service.bdd.steps;

import com.aniri.workflow_service.bdd.support.World;
import com.aniri.workflow_service.domain.booking.model.BookingEntity;
import com.aniri.workflow_service.domain.booking.model.BookingRepository;
import com.aniri.workflow_service.domain.booking.model.ResourceType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

public class BookingSteps {

    @Autowired World world;
    @Autowired BookingRepository bookingRepository;

    @Given("a flight-booking request for employee {string} to {string}")
    public void buildFlightBookingRequest(final String employeeId, final String destination) {
        final Map<String, Object> body = new HashMap<>();
        body.put("employeeId", employeeId);
        body.put("resourceType", "FLIGHT");
        body.put("destination", destination);
        body.put("departureDate", "2027-11-05T08:00:00Z");
        body.put("returnDate", "2027-11-08T18:00:00Z");
        body.put("travelerCount", 1);
        body.put("costCenterRef", "CC-456");
        world.setRequestBody(body);
    }

    @Given("a hotel booking exists for employee {string} in {string}")
    public void seedHotelBooking(final String employeeId, final String destination) {
        final BookingEntity saved = bookingRepository.save(BookingEntity.builder()
                .employeeId(employeeId)
                .resourceType(ResourceType.HOTEL)
                .destination(destination)
                .departureDate(OffsetDateTime.parse("2027-11-05T08:00:00Z"))
                .returnDate(OffsetDateTime.parse("2027-11-08T18:00:00Z"))
                .travelerCount(1)
                .costCenterRef("CC-456")
                .tripId(randomUUID())
                .build());
        world.setCreatedBookingId(saved.getId().toString());
    }

    @When("I replay the booking request with the same idempotency key")
    public void replaySameKey() {
        // Stash first response, fire a second request with same key + body, then compare ids.
        final String firstId = world.getLastResponse().jsonPath().getString("id");
        world.setCreatedBookingId(firstId);
        world.setLastResponse(
                given().header("Idempotency-Key", world.getIdempotencyKey())
                        .contentType(ContentType.JSON).body(world.getRequestBody())
                        .when().post("/bookings"));
    }

    @Then("the response returns the same booking id as the first call")
    public void sameBookingId() {
        assertThat(world.getLastResponse().jsonPath().getString("id"))
                .isEqualTo(world.getCreatedBookingId());
    }

    @Then("only one booking exists in the database")
    public void onlyOneBookingExists() {
        assertThat(bookingRepository.count()).isEqualTo(1L);
    }
}
