package com.aniri.workflow_service.domain.search.model;

import com.aniri.workflow_service.web.model.BookingSearch;
import com.aniri.workflow_service.web.model.ResourceType;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface SearchMapper {

    BookingSearch toDto(SearchResult result);

    List<BookingSearch> toDtoList(List<SearchResult> results);

    ResourceType toWireResourceType(com.aniri.workflow_service.domain.booking.model.ResourceType domain);
}
