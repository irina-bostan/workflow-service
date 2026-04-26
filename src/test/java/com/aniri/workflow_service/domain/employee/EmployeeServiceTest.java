package com.aniri.workflow_service.domain.employee;

import com.aniri.workflow_service.domain.employee.exception.DuplicateEmployeeException;
import com.aniri.workflow_service.domain.employee.model.EmployeeEntity;
import com.aniri.workflow_service.domain.employee.model.EmployeeMapper;
import com.aniri.workflow_service.domain.employee.model.EmployeeRepository;
import com.aniri.workflow_service.web.model.Employee;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    private static final String EMPLOYEE_ID = "EMP9876";
    private static final String EMAIL = "alice.smith@techquarter.com";

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeMapper employeeMapper;

    @InjectMocks private EmployeeService employeeService;

    @Test
    void register_validRequest_savesAndReturnsDto() {
        final Employee dto = newWireEmployee();
        final EmployeeEntity toSave = EmployeeEntity.builder().employeeId(EMPLOYEE_ID).build();
        final EmployeeEntity saved = EmployeeEntity.builder().id(UUID.randomUUID()).employeeId(EMPLOYEE_ID).build();
        final Employee returned = new Employee().employeeId(EMPLOYEE_ID);

        when(employeeRepository.existsByEmployeeId(EMPLOYEE_ID)).thenReturn(false);
        when(employeeRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(employeeMapper.toEntity(dto)).thenReturn(toSave);
        when(employeeRepository.save(toSave)).thenReturn(saved);
        when(employeeMapper.toDto(saved)).thenReturn(returned);

        final Employee result = employeeService.register(dto);

        assertThat(result).isSameAs(returned);
        verify(employeeRepository).save(toSave);
    }

    @Test
    void register_employeeIdAlreadyTaken_throwsDuplicateEmployeeException() {
        final Employee dto = newWireEmployee();

        when(employeeRepository.existsByEmployeeId(EMPLOYEE_ID)).thenReturn(true);

        assertThatThrownBy(() -> employeeService.register(dto))
                .isInstanceOf(DuplicateEmployeeException.class)
                .hasMessageContaining("employeeId")
                .hasMessageContaining(EMPLOYEE_ID);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void register_emailAlreadyTaken_throwsDuplicateEmployeeException() {
        final Employee dto = newWireEmployee();

        when(employeeRepository.existsByEmployeeId(EMPLOYEE_ID)).thenReturn(false);
        when(employeeRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> employeeService.register(dto))
                .isInstanceOf(DuplicateEmployeeException.class)
                .hasMessageContaining("email")
                .hasMessageContaining(EMAIL);

        verify(employeeRepository, never()).save(any());
    }

    private static Employee newWireEmployee() {
        return new Employee()
                .employeeId(EMPLOYEE_ID)
                .firstName("Alice")
                .lastName("Smith")
                .email(EMAIL)
                .department("Engineering");
    }
}
