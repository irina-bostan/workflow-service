package com.aniri.workflow_service.application.duffel;

import com.aniri.workflow_service.application.duffel.DuffelApiDtos.*;
import com.aniri.workflow_service.application.properties.DuffelProperties;
import com.aniri.workflow_service.domain.booking.model.ResourceType;
import com.aniri.workflow_service.domain.search.model.SearchRequest;
import com.aniri.workflow_service.domain.search.model.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "duffel.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DuffelSearchProvider {

    private static final int RESULT_LIMIT = 5;
    private static final String CABIN_CLASS = "economy";
    private static final int STAYS_RADIUS_KM = 10;
    /** Duffel offers don't carry remaining-seat counts; the UI expects a number, so we stub one. */
    private static final int FLIGHT_AVAILABLE_SEATS_PLACEHOLDER = 9;
    private static final int ISO_LOCAL_DATETIME_LENGTH = 19;

    private static final String FLIGHT_OFFERS_PATH = "/air/offer_requests?return_offers=true";
    private static final String STAYS_SEARCH_PATH = "/stays/search";
    private static final String CITIES_RESOURCE = "duffel/cities.csv";
    private static final Map<String, LatLon> CITY_COORDS = loadCityCoords();

    private final RestClient duffelRestClient;
    private final DuffelProperties duffelProperties;

    public List<SearchResult> search(final SearchRequest request) {
        try {
            return request.resourceType() == ResourceType.FLIGHT
                    ? searchFlights(request)
                    : searchStays(request);
        } catch (final HttpClientErrorException e) {
            // 403 = "feature not enabled" (Stays often gated separately on Duffel accounts);
            // 422 = invalid request shape; etc. These are user-actionable and benign at the
            // log level — a WARN is enough. SearchService will decide whether to fall back
            // to mock based on the empty result.
            log.warn("Duffel {} search returned {}: {}",
                    request.resourceType(), e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (final RestClientException e) {
            log.error("Duffel API call failed", e);
            return List.of();
        }
    }

    private List<SearchResult> searchFlights(final SearchRequest request) {
        final OfferRequestBody body = new OfferRequestBody(new OfferRequestData(
                buildSlices(request),
                passengers(request.travelerCount()),
                CABIN_CLASS));

        final OfferRequestResponse response = post(FLIGHT_OFFERS_PATH, body, OfferRequestResponse.class);
        if (response == null || response.data() == null || response.data().offers() == null) {
            return List.of();
        }
        log.info("Duffel returned {} flight offers", response.data().offers().size());
        return response.data().offers().stream()
                .limit(RESULT_LIMIT)
                .map(this::mapFlightOffer)
                .toList();
    }

    private List<SearchResult> searchStays(final SearchRequest request) {
        final LatLon coords = CITY_COORDS.get(request.destination());
        if (coords == null) {
            log.warn("No coordinates configured for stays destination: {}", request.destination());
            return List.of();
        }

        final OffsetDateTime checkOut = request.returnDate() != null
                ? request.returnDate()
                : request.departureDate().plusDays(1);

        final StaysSearchBody body = new StaysSearchBody(
                toDateString(request.departureDate()),
                toDateString(checkOut),
                1,
                guests(request.travelerCount()),
                new StayLocation(coords.latitude(), coords.longitude(), STAYS_RADIUS_KM));

        final StaysSearchResponse response = post(STAYS_SEARCH_PATH, body, StaysSearchResponse.class);
        if (response == null || response.data() == null) {
            return List.of();
        }
        log.info("Duffel returned {} stay results", response.data().size());
        return response.data().stream()
                .filter(r -> r.rates() != null && !r.rates().isEmpty())
                .limit(RESULT_LIMIT)
                .map(r -> mapStayResult(r, request.destination(), request.departureDate(), checkOut))
                .toList();
    }

    private <T> T post(final String path, final Object body, final Class<T> responseType) {
        return duffelRestClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(responseType);
    }

    private List<FlightSlice> buildSlices(final SearchRequest request) {
        final String origin = duffelProperties.defaultOrigin();
        final FlightSlice outbound = new FlightSlice(origin, request.destination(), toDateString(request.departureDate()));
        return request.returnDate() == null
                ? List.of(outbound)
                : List.of(outbound, new FlightSlice(request.destination(), origin, toDateString(request.returnDate())));
    }

    private static List<Passenger> passengers(final int count) {
        return Collections.nCopies(count, new Passenger("adult"));
    }

    private static List<StayGuest> guests(final int count) {
        return Collections.nCopies(count, new StayGuest("adult"));
    }

    private SearchResult mapFlightOffer(final FlightOffer offer) {
        final List<Segment> segments = offer.slices().getFirst().segments();
        final Segment first = segments.getFirst();
        final Segment last = segments.getLast();
        return SearchResult.builder()
                .providerId(offer.id())
                .resourceType(ResourceType.FLIGHT)
                .origin(first.origin().iataCode())
                .destination(last.destination().iataCode())
                .departureTime(parseDateTime(first.departingAt()))
                .arrivalTime(parseDateTime(last.arrivingAt()))
                .availableSeats(FLIGHT_AVAILABLE_SEATS_PLACEHOLDER)
                .pricePerPerson(new BigDecimal(offer.totalAmount()))
                .currency(offer.totalCurrency())
                .providerName(offer.owner().name())
                .build();
    }

    private SearchResult mapStayResult(final StayResult result,
                                       final String destination,
                                       final OffsetDateTime checkIn,
                                       final OffsetDateTime checkOut) {
        final Accommodation accommodation = result.accommodation();
        final StayRate rate = result.rates().getFirst();
        return SearchResult.builder()
                .providerId(accommodation.id())
                .resourceType(ResourceType.HOTEL)
                .destination(destination)
                .departureTime(checkIn)
                .arrivalTime(checkOut)
                .availableSeats(rate.availableRooms() != null ? rate.availableRooms() : 1)
                .pricePerPerson(new BigDecimal(rate.totalAmount().amount()))
                .currency(rate.totalAmount().currency())
                .providerName(accommodation.name())
                .build();
    }

    private static String toDateString(final OffsetDateTime dt) {
        return dt.toLocalDate().toString();
    }

    /**
     * Duffel returns either {@code 2026-11-06T10:00:00} (no offset) or
     * {@code 2026-11-06T10:00:00+00:00}. Both shapes are coerced to UTC.
     */
    private static OffsetDateTime parseDateTime(final String at) {
        if (at == null || at.isBlank()) return null;
        return at.length() > ISO_LOCAL_DATETIME_LENGTH
                ? OffsetDateTime.parse(at)
                : LocalDateTime.parse(at).atOffset(ZoneOffset.UTC);
    }

    private static Map<String, LatLon> loadCityCoords() {
        try (InputStream in = new ClassPathResource(CITIES_RESOURCE).getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.split(","))
                    .collect(Collectors.toUnmodifiableMap(
                            parts -> parts[0].trim(),
                            parts -> new LatLon(Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim()))));
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to load " + CITIES_RESOURCE, e);
        }
    }

    private record LatLon(double latitude, double longitude) {}
}
