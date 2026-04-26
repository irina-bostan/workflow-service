Feature: Appointment scheduling

  Background:
    Given employee "EMP9876" with email "alice@techquarter.com" is already registered

  Scenario: Successfully schedule a SPA appointment for an existing hotel booking
    Given a hotel booking exists for employee "EMP9876" in "NYC"
    And an appointment request of type "SPA" at "2027-11-06T10:00:00Z"
    When I POST it to the appointments endpoint for the seeded booking
    Then the response status is 201
    And the response field "appointmentType" equals "SPA"

  Scenario: Reject appointment for a non-existent booking
    Given an appointment request of type "SPA" at "2027-11-06T10:00:00Z"
    When I POST it to the appointments endpoint for booking "00000000-0000-0000-0000-000000000000"
    Then the response status is 404
    And the response field "reasonCode" equals "BOOKING_NOT_FOUND"
