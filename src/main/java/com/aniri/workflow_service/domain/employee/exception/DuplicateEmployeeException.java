package com.aniri.workflow_service.domain.employee.exception;

public class DuplicateEmployeeException extends RuntimeException {

    public DuplicateEmployeeException(final String field, final String value) {
        super("Employee already exists with " + field + ": " + value);
    }
}
