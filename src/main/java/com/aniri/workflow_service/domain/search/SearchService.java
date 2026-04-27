package com.aniri.workflow_service.domain.search;

import com.aniri.workflow_service.application.duffel.DuffelSearchProvider;
import com.aniri.workflow_service.domain.booking.model.ResourceType;
import com.aniri.workflow_service.domain.search.model.SearchMapper;
import com.aniri.workflow_service.domain.search.model.SearchRequest;
import com.aniri.workflow_service.domain.search.model.SearchResult;
import com.aniri.workflow_service.web.model.BookingSearch;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final Optional<DuffelSearchProvider> duffelProvider;
    private final SearchMapper searchMapper;

    @Cacheable(
            cacheNames = "searchResults",
            key = "#request.resourceType() + ':' + #request.destination() + ':' + #request.departureDate()",
            unless = "#result.isEmpty()"
    )
    public List<BookingSearch> search(final SearchRequest request) {
        final List<SearchResult> direct = duffelProvider
                .map(provider -> provider.search(request))
                .orElseGet(() -> buildMockResults(request));

        // Duffel Stays is enabled separately on accounts (basic tokens get flights
        // but not hotels — 403 "feature not enabled"). When a hotel search comes
        // back empty, fall back to mock so local dev still has data.
        final List<SearchResult> results =
                direct.isEmpty() && request.resourceType() == ResourceType.HOTEL
                        ? buildMockResults(request)
                        : direct;
        return searchMapper.toDtoList(results);
    }

    private List<SearchResult> buildMockResults(final SearchRequest request) {
        final OffsetDateTime departure = request.departureDate();
        if (request.resourceType() == ResourceType.FLIGHT) {
            return mockFlights(request.destination(), departure);
        }
        // Hotel search without an explicit returnDate gets a default check-out one day after
        // check-in — matches DuffelSearchProvider.searchStays. Without this, mockHotel would
        // emit a SearchResult with arrivalTime=null, the UI would forward returnDate=undefined
        // to POST /bookings, and the controller's validateBooking would 422 with
        // "Hotel bookings require a returnDate". Belt-and-suspenders alongside the UI guard.
        final OffsetDateTime checkOut = request.returnDate() != null
                ? request.returnDate()
                : (departure != null ? departure.plusDays(1) : null);
        return mockHotel(request.destination(), departure, checkOut);
    }

    private List<SearchResult> mockFlights(final String destination, final OffsetDateTime departure) {
        return List.of(
                SearchResult.builder()
                        .providerId("BA-001")
                        .resourceType(ResourceType.FLIGHT)
                        .origin("LHR")
                        .destination(destination)
                        .departureTime(departure)
                        .arrivalTime(departure != null ? departure.plusHours(7) : null)
                        .availableSeats(42)
                        .pricePerPerson(BigDecimal.valueOf(550.00))
                        .currency("GBP")
                        .providerName("British Airways")
                        .build(),
                SearchResult.builder()
                        .providerId("VS-002")
                        .resourceType(ResourceType.FLIGHT)
                        .origin("LHR")
                        .destination(destination)
                        .departureTime(departure != null ? departure.plusHours(2) : null)
                        .arrivalTime(departure != null ? departure.plusHours(9) : null)
                        .availableSeats(18)
                        .pricePerPerson(BigDecimal.valueOf(490.00))
                        .currency("GBP")
                        .providerName("Virgin Atlantic")
                        .build()
        );
    }

    private List<SearchResult> mockHotel(final String destination,
                                         final OffsetDateTime checkIn,
                                         final OffsetDateTime checkOut) {
        return List.of(SearchResult.builder()
                .providerId("HLT-001")
                .resourceType(ResourceType.HOTEL)
                .destination(destination)
                .departureTime(checkIn)
                .arrivalTime(checkOut)
                .availableSeats(10)
                .pricePerPerson(BigDecimal.valueOf(250.00))
                .currency("GBP")
                .providerName("Hilton " + destination)
                .build());
    }
}
