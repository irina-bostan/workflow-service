package com.aniri.workflow_service.web.api;

import com.aniri.workflow_service.domain.employee.EmployeeService;
import com.aniri.workflow_service.web.model.Employee;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmployeesApiDelegateImpl implements EmployeesApiDelegate {

    private final EmployeeService employeeService;

    @Override
    public ResponseEntity<Employee> registerEmployee(final Employee employee) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.register(employee));
    }
}
