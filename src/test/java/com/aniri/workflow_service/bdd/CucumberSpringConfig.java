package com.aniri.workflow_service.bdd;

import io.cucumber.spring.CucumberContextConfiguration;
import io.restassured.RestAssured;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import jakarta.annotation.PostConstruct;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class CucumberSpringConfig {

    @LocalServerPort int port;

    @PostConstruct
    void bindRestAssuredPort() {
        RestAssured.port = port;
    }
}
