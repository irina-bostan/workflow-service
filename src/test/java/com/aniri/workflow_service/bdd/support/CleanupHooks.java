package com.aniri.workflow_service.bdd.support;

import com.aniri.workflow_service.domain.appointment.model.AppointmentRepository;
import com.aniri.workflow_service.domain.booking.model.BookingRepository;
import com.aniri.workflow_service.domain.employee.model.EmployeeRepository;
import com.aniri.workflow_service.domain.outbox.OutboxRepository;
import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;

public class CleanupHooks {

    @Autowired AppointmentRepository appointmentRepository;
    @Autowired OutboxRepository outboxRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired EmployeeRepository employeeRepository;

    @Before
    public void cleanDatabase() {
        appointmentRepository.deleteAll();
        outboxRepository.deleteAll();
        bookingRepository.deleteAll();
        employeeRepository.deleteAll();
    }
}
