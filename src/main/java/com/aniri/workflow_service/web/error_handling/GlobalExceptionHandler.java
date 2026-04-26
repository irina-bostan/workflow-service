package com.aniri.workflow_service.web.error_handling;

import com.aniri.workflow_service.domain.booking.exception.BookingNotFoundException;
import com.aniri.workflow_service.domain.booking.exception.InvalidBookingRequestException;
import com.aniri.workflow_service.domain.employee.exception.DuplicateEmployeeException;
import com.aniri.workflow_service.domain.employee.exception.EmployeeNotFoundException;
import com.aniri.workflow_service.web.model.Error;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String SOURCE = "com.aniri.workflow-service";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Error handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error("VALIDATION_ERROR", "Request validation failed", details, false);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Error handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        String details = ex.getAllValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> {
                            String field = e instanceof FieldError fe ? fe.getField() : r.getMethodParameter().getParameterName();
                            return field + ": " + e.getDefaultMessage();
                        }))
                .collect(Collectors.joining("; "));
        return error("VALIDATION_ERROR", "Request validation failed", details, false);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Error handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return error("MISSING_HEADER", "Missing required header: " + ex.getHeaderName(), null, false);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Error handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return error("MISSING_PARAMETER", "Missing required parameter: " + ex.getParameterName(), null, false);
    }

    @ExceptionHandler(EmployeeNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Error handleEmployeeNotFound(EmployeeNotFoundException ex, HttpServletRequest request) {
        return error("EMPLOYEE_NOT_FOUND", ex.getMessage(), null, false);
    }

    @ExceptionHandler(BookingNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Error handleBookingNotFound(BookingNotFoundException ex, HttpServletRequest request) {
        return error("BOOKING_NOT_FOUND", ex.getMessage(), null, false);
    }

    @ExceptionHandler(DuplicateEmployeeException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Error handleDuplicateEmployee(DuplicateEmployeeException ex, HttpServletRequest request) {
        return error("DUPLICATE_EMPLOYEE", ex.getMessage(), null, false);
    }

    @ExceptionHandler(InvalidBookingRequestException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Error handleInvalidBooking(InvalidBookingRequestException ex, HttpServletRequest request) {
        return error("INVALID_BOOKING", ex.getMessage(), null, false);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Error handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation at {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return error("DATA_INTEGRITY_VIOLATION", "Resource already exists or violates a constraint", null, false);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Error handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Optimistic lock conflict at {}", request.getRequestURI());
        return error("CONCURRENT_MODIFICATION", "Concurrent modification detected, please retry", null, true);
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Error handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        log.error("Database error at {}", request.getRequestURI(), ex);
        return error("DATABASE_UNAVAILABLE", "Database temporarily unavailable", null, true);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Error handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        return error("INTERNAL_ERROR", "An unexpected error occurred", null, false);
    }

    private Error error(String reasonCode, String description, String details, boolean recoverable) {
        return new Error()
                .source(SOURCE)
                .reasonCode(reasonCode)
                .description(description)
                .details(details)
                .isRecoverable(recoverable);
    }
}
