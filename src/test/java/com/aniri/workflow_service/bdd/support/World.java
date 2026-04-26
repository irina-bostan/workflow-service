package com.aniri.workflow_service.bdd.support;

import io.cucumber.spring.ScenarioScope;
import io.restassured.response.Response;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-scenario state shared across step-definition classes — request body, last
 * response, and any IDs the scenario wants to thread between steps.
 */
@Component
@ScenarioScope
@Getter
@Setter
public class World {

    private Map<String, Object> requestBody = new HashMap<>();
    private Response lastResponse;
    private String idempotencyKey;
    private String createdBookingId;
}
