package com.aniri.workflow_service.bdd.support;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generic HTTP + assertion steps reused across all features. Domain-specific
 * setup steps live in their own classes and populate {@link World} so these
 * generic verbs operate on whatever the scenario built up.
 */
public class HttpSteps {

    @Autowired World world;

    @When("I POST the request to {string}")
    public void postRequest(final String path) {
        world.setLastResponse(
                given().contentType(ContentType.JSON).body(world.getRequestBody())
                        .when().post(path));
    }

    @When("I POST the request to {string} with idempotency key {string}")
    public void postWithIdempotencyKey(final String path, final String key) {
        world.setIdempotencyKey(key);
        world.setLastResponse(
                given().header("Idempotency-Key", key).contentType(ContentType.JSON).body(world.getRequestBody())
                        .when().post(path));
    }

    @When("I GET {string}")
    public void getRequest(final String path) {
        world.setLastResponse(given().when().get(path));
    }

    @Then("the response status is {int}")
    public void responseStatus(final int expected) {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(expected);
    }

    @Then("the response field {string} equals {string}")
    public void responseFieldEquals(final String jsonPath, final String expected) {
        assertThat(world.getLastResponse().jsonPath().getString(jsonPath)).isEqualTo(expected);
    }

    @Then("the response field {string} is not null")
    public void responseFieldNotNull(final String jsonPath) {
        final Object value = world.getLastResponse().jsonPath().get(jsonPath);
        assertThat(value).isNotNull();
    }
}
