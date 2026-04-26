Feature: Booking creation

  Background:
    Given employee "EMP9876" with email "alice@techquarter.com" is already registered

  Scenario: Successfully create a flight booking
    Given a flight-booking request for employee "EMP9876" to "NYC"
    When I POST the request to "/bookings" with idempotency key "11111111-1111-1111-1111-111111111111"
    Then the response status is 201
    And the response field "destination" equals "NYC"

  Scenario: Idempotent replay returns the same booking
    Given a flight-booking request for employee "EMP9876" to "NYC"
    When I POST the request to "/bookings" with idempotency key "22222222-2222-2222-2222-222222222222"
    And I replay the booking request with the same idempotency key
    Then the response status is 201
    And the response returns the same booking id as the first call
    And only one booking exists in the database

  Scenario: Reject booking for an unknown employee
    Given a flight-booking request for employee "DOES-NOT-EXIST" to "NYC"
    When I POST the request to "/bookings" with idempotency key "33333333-3333-3333-3333-333333333333"
    Then the response status is 404
    And the response field "reasonCode" equals "EMPLOYEE_NOT_FOUND"
