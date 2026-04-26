package com.aniri.workflow_service.bdd.steps;

import com.aniri.workflow_service.bdd.support.World;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

public class AppointmentSteps {

    @Autowired World world;

    @Given("an appointment request of type {string} at {string}")
    public void buildAppointmentRequest(final String type, final String scheduledAt) {
        final Map<String, Object> body = new HashMap<>();
        body.put("appointmentType", type);
        body.put("scheduledAt", scheduledAt);
        world.setRequestBody(body);
    }

    @When("I POST it to the appointments endpoint for the seeded booking")
    public void postForSeededBooking() {
        world.setLastResponse(
                given().contentType(ContentType.JSON).body(world.getRequestBody())
                        .when().post("/bookings/{bookingId}/appointments", world.getCreatedBookingId()));
    }

    @When("I POST it to the appointments endpoint for booking {string}")
    public void postForExplicitBooking(final String bookingId) {
        world.setLastResponse(
                given().contentType(ContentType.JSON).body(world.getRequestBody())
                        .when().post("/bookings/{bookingId}/appointments", bookingId));
    }
}
