package com.aniri.workflow_service.domain.employee.model;

import com.aniri.workflow_service.web.model.Employee;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface EmployeeMapper {

    @Mapping(target = "id", ignore = true)
    EmployeeEntity toEntity(Employee dto);

    Employee toDto(EmployeeEntity entity);
}
