Feature: Booking search

  Scenario: Search returns 200 for a valid query
    When I GET "/bookings/search?resourceType=FLIGHT&destination=NYC"
    Then the response status is 200

  Scenario: Search rejects a request missing destination
    When I GET "/bookings/search?resourceType=FLIGHT"
    Then the response status is 400
