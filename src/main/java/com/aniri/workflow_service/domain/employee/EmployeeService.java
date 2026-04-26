package com.aniri.workflow_service.domain.employee;

import com.aniri.workflow_service.domain.employee.exception.DuplicateEmployeeException;
import com.aniri.workflow_service.domain.employee.model.EmployeeEntity;
import com.aniri.workflow_service.domain.employee.model.EmployeeMapper;
import com.aniri.workflow_service.domain.employee.model.EmployeeRepository;
import com.aniri.workflow_service.web.model.Employee;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeMapper employeeMapper;

    @Transactional
    public Employee register(final Employee employee) {
        validateEmployee(employee.getEmployeeId(), employee.getEmail());

        final EmployeeEntity saved = employeeRepository.save(employeeMapper.toEntity(employee));
        log.info("Registered employee id={} employeeId={}", saved.getId(), saved.getEmployeeId());

        return employeeMapper.toDto(saved);
    }

    private void validateEmployee(final String employeeId, final String email) {
        if (employeeRepository.existsByEmployeeId(employeeId)) {
            throw new DuplicateEmployeeException("employeeId", employeeId);
        }
        if (employeeRepository.existsByEmail(email)) {
            throw new DuplicateEmployeeException("email", email);
        }
    }
}
