package com.aniri.workflow_service.web.api;

import com.aniri.workflow_service.application.properties.CorsProperties;
import com.aniri.workflow_service.domain.employee.EmployeeService;
import com.aniri.workflow_service.domain.employee.exception.DuplicateEmployeeException;
import com.aniri.workflow_service.web.error_handling.GlobalExceptionHandler;
import com.aniri.workflow_service.web.model.Employee;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({EmployeesApiDelegateImpl.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(CorsProperties.class)
@AutoConfigureMockMvc(addFilters = false)
class EmployeesApiDelegateImplTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private EmployeeService employeeService;

    @Test
    void registerEmployee_validRequest_returns201WithBody() throws Exception {
        final Employee response = new Employee()
                .id(UUID.randomUUID())
                .employeeId("EMP9876")
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@techquarter.com")
                .department("Engineering");
        when(employeeService.register(any())).thenReturn(response);

        final Employee request = new Employee()
                .employeeId("EMP9876")
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@techquarter.com")
                .department("Engineering");

        mockMvc.perform(post("/employees")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeId").value("EMP9876"))
                .andExpect(jsonPath("$.email").value("alice@techquarter.com"));
    }

    @Test
    void registerEmployee_missingRequiredField_returns400ValidationError() throws Exception {
        final String body = """
                {
                  "firstName": "Alice",
                  "lastName": "Smith",
                  "email": "alice@techquarter.com",
                  "department": "Engineering"
                }
                """;

        mockMvc.perform(post("/employees")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reasonCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").value(org.hamcrest.Matchers.containsString("employeeId")));
    }

    @Test
    void registerEmployee_duplicate_returns409Conflict() throws Exception {
        when(employeeService.register(any()))
                .thenThrow(new DuplicateEmployeeException("employeeId", "EMP9876"));

        final Employee request = new Employee()
                .employeeId("EMP9876")
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@techquarter.com")
                .department("Engineering");

        mockMvc.perform(post("/employees")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasonCode").value("DUPLICATE_EMPLOYEE"));
    }
}
