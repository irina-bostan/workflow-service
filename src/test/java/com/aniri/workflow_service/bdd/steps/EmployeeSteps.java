package com.aniri.workflow_service.bdd.steps;

import com.aniri.workflow_service.bdd.support.World;
import com.aniri.workflow_service.domain.employee.model.EmployeeEntity;
import com.aniri.workflow_service.domain.employee.model.EmployeeRepository;
import io.cucumber.java.en.Given;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

public class EmployeeSteps {

    @Autowired World world;
    @Autowired EmployeeRepository employeeRepository;

    @Given("employee {string} with email {string} is already registered")
    public void seedEmployee(final String employeeId, final String email) {
        employeeRepository.save(EmployeeEntity.builder()
                .employeeId(employeeId)
                .firstName("Seeded").lastName("User")
                .email(email)
                .department("Engineering")
                .build());
    }

    @Given("a new-employee request for {string} with email {string}")
    public void buildEmployeeRequest(final String employeeId, final String email) {
        final Map<String, Object> body = new HashMap<>();
        body.put("employeeId", employeeId);
        body.put("firstName", "Alice");
        body.put("lastName", "Smith");
        body.put("email", email);
        body.put("department", "Engineering");
        body.put("costCentreDefault", "CC-456");
        world.setRequestBody(body);
    }
}
