package com.aniri.workflow_service.integration;

import com.aniri.workflow_service.domain.employee.model.EmployeeRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EmployeeRegisterIT {

    @LocalServerPort int port;
    @Autowired EmployeeRepository employeeRepository;

    @BeforeEach
    void setup() {
        RestAssured.port = port;
        employeeRepository.deleteAll();
    }

    @Test
    void registerEmployee_validRequest_persistsAndReturns201() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "employeeId": "EMP9876",
                          "firstName": "Alice",
                          "lastName": "Smith",
                          "email": "alice@techquarter.com",
                          "department": "Engineering",
                          "costCentreDefault": "CC-456"
                        }
                        """)
                .when().post("/employees")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("employeeId", equalTo("EMP9876"))
                .body("email", equalTo("alice@techquarter.com"));

        assertThat(employeeRepository.existsByEmployeeId("EMP9876")).isTrue();
    }

    @Test
    void registerEmployee_duplicateEmployeeId_returns409() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "employeeId": "EMP9876",
                          "firstName": "Alice",
                          "lastName": "Smith",
                          "email": "alice@techquarter.com",
                          "department": "Engineering"
                        }
                        """)
                .when().post("/employees")
                .then().statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "employeeId": "EMP9876",
                          "firstName": "Bob",
                          "lastName": "Jones",
                          "email": "bob@techquarter.com",
                          "department": "Engineering"
                        }
                        """)
                .when().post("/employees")
                .then()
                .statusCode(409)
                .body("reasonCode", equalTo("DUPLICATE_EMPLOYEE"));
    }
}
