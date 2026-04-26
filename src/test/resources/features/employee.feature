Feature: Employee registration

  Scenario: Successfully register a new employee
    Given a new-employee request for "EMP9876" with email "alice@techquarter.com"
    When I POST the request to "/employees"
    Then the response status is 201
    And the response field "employeeId" equals "EMP9876"
    And the response field "id" is not null

  Scenario: Reject a duplicate employee id
    Given employee "EMP9876" with email "alice@techquarter.com" is already registered
    And a new-employee request for "EMP9876" with email "bob@techquarter.com"
    When I POST the request to "/employees"
    Then the response status is 409
    And the response field "reasonCode" equals "DUPLICATE_EMPLOYEE"
