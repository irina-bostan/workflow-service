package com.aniri.workflow_service.application.duffel;

import java.util.List;

/**
 * Container for the Duffel HTTP request/response shapes used by {@link DuffelSearchProvider}.
 * Records mirror Duffel's wire JSON (snake-case is handled by the {@code RestClient}'s ObjectMapper
 * configured in {@link DuffelConfig}).
 */
final class DuffelApiDtos {

    private DuffelApiDtos() {
    }

    // ---- Flights: request ----
    record OfferRequestBody(OfferRequestData data) {
    }

    record OfferRequestData(List<FlightSlice> slices, List<Passenger> passengers, String cabinClass) {
    }

    record FlightSlice(String origin, String destination, String departureDate) {
    }

    record Passenger(String type) {
    }

    // ---- Flights: response ----
    record OfferRequestResponse(OfferResponseData data) {
    }

    record OfferResponseData(List<FlightOffer> offers) {
    }

    record FlightOffer(String id, String totalAmount, String totalCurrency, Airline owner,
                       List<FlightSliceResponse> slices) {
    }

    record Airline(String iataCode, String name) {
    }

    record FlightSliceResponse(List<Segment> segments) {
    }

    record Segment(Airport origin, Airport destination, String departingAt, String arrivingAt) {
    }

    record Airport(String iataCode) {
    }

    // ---- Stays: request ----
    record StaysSearchBody(String checkInDate, String checkOutDate, int rooms, List<StayGuest> guests,
                           StayLocation location) {
    }

    record StayGuest(String type) {
    }

    record StayLocation(double latitude, double longitude, int radius) {
    }

    // ---- Stays: response ----
    record StaysSearchResponse(List<StayResult> data) {
    }

    record StayResult(Accommodation accommodation, List<StayRate> rates) {
    }

    record Accommodation(String id, String name) {
    }

    record StayRate(String id, RatePrice totalAmount, Integer availableRooms) {
    }

    record RatePrice(String amount, String currency) {
    }
}
