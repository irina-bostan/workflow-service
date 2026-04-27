package com.aniri.workflow_service.domain.search;

import com.aniri.workflow_service.application.duffel.DuffelSearchProvider;
import com.aniri.workflow_service.domain.booking.model.ResourceType;
import com.aniri.workflow_service.domain.search.model.SearchMapper;
import com.aniri.workflow_service.domain.search.model.SearchRequest;
import com.aniri.workflow_service.domain.search.model.SearchResult;
import com.aniri.workflow_service.web.model.BookingSearch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock private DuffelSearchProvider duffelProvider;
    @Mock private SearchMapper searchMapper;

    @Test
    void search_noDuffelProviderConfigured_fallsBackToMockResults() {
        final SearchService service = new SearchService(Optional.empty(), searchMapper);
        final SearchRequest request = newRequest();

        when(searchMapper.toDtoList(any())).thenReturn(List.of(new BookingSearch().providerId("BA-001")));

        final List<BookingSearch> results = service.search(request);

        assertThat(results).extracting(BookingSearch::getProviderId).containsExactly("BA-001");
        verify(searchMapper).toDtoList(any());
        verifyNoInteractions(duffelProvider);
    }

    @Test
    void search_withDuffelProvider_delegatesToProvider() {
        final SearchService service = new SearchService(Optional.of(duffelProvider), searchMapper);
        final SearchRequest request = newRequest();
        final SearchResult duffelResult = SearchResult.builder().providerId("DUFFEL-001").build();

        when(duffelProvider.search(request)).thenReturn(List.of(duffelResult));
        when(searchMapper.toDtoList(List.of(duffelResult)))
                .thenReturn(List.of(new BookingSearch().providerId("DUFFEL-001")));

        final List<BookingSearch> results = service.search(request);

        assertThat(results).extracting(BookingSearch::getProviderId).containsExactly("DUFFEL-001");
        verify(duffelProvider).search(request);
    }

    @Test
    void search_hotelMockFallback_defaultsCheckOutToCheckInPlusOneWhenNotProvided() {
        final SearchService service = new SearchService(Optional.empty(), searchMapper);
        final SearchRequest request = SearchRequest.builder()
                .resourceType(ResourceType.HOTEL)
                .destination("NYC")
                .departureDate(OffsetDateTime.parse("2027-11-05T08:00:00Z"))
                .travelerCount(1)
                .build();

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<SearchResult>> captor = ArgumentCaptor.forClass(List.class);
        when(searchMapper.toDtoList(captor.capture())).thenReturn(List.of());

        service.search(request);

        // arrivalTime must be populated; otherwise the UI sends booking.returnDate=null,
        // which the controller's validateBooking rejects with 422.
        final SearchResult result = captor.getValue().getFirst();
        assertThat(result.arrivalTime()).isEqualTo(OffsetDateTime.parse("2027-11-06T08:00:00Z"));
        assertThat(result.departureTime()).isEqualTo(OffsetDateTime.parse("2027-11-05T08:00:00Z"));
    }

    private static SearchRequest newRequest() {
        return SearchRequest.builder()
                .resourceType(ResourceType.FLIGHT)
                .destination("NYC")
                .departureDate(OffsetDateTime.parse("2027-11-05T08:00:00Z"))
                .travelerCount(1)
                .build();
    }
}
