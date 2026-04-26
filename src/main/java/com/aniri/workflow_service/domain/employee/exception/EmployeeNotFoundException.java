package com.aniri.workflow_service.domain.employee.exception;

public class EmployeeNotFoundException extends RuntimeException {

    public EmployeeNotFoundException(final String employeeId) {
        super("Employee not found: " + employeeId);
    }
}
