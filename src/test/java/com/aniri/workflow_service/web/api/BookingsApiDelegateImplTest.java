package com.aniri.workflow_service.web.api;

import com.aniri.workflow_service.application.properties.CorsProperties;
import com.aniri.workflow_service.domain.appointment.AppointmentService;
import com.aniri.workflow_service.domain.booking.BookingService;
import com.aniri.workflow_service.domain.booking.exception.BookingNotFoundException;
import com.aniri.workflow_service.domain.booking.exception.InvalidBookingRequestException;
import com.aniri.workflow_service.domain.booking.model.BookingEntity;
import com.aniri.workflow_service.domain.booking.model.BookingMapper;
import com.aniri.workflow_service.domain.search.SearchService;
import com.aniri.workflow_service.web.error_handling.GlobalExceptionHandler;
import com.aniri.workflow_service.web.model.Appointment;
import com.aniri.workflow_service.web.model.AppointmentType;
import com.aniri.workflow_service.web.model.Booking;
import com.aniri.workflow_service.web.model.BookingSearch;
import com.aniri.workflow_service.web.model.ResourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({BookingsApiDelegateImpl.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(CorsProperties.class)
@AutoConfigureMockMvc(addFilters = false)
class BookingsApiDelegateImplTest {

    private static final String IDEMPOTENCY_KEY = "11111111-1111-1111-1111-111111111111";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private BookingService bookingService;
    @MockitoBean private SearchService searchService;
    @MockitoBean private AppointmentService appointmentService;
    @MockitoBean private BookingMapper bookingMapper;

    // ─────────────────────────────── POST /bookings ───────────────────────────────

    @Test
    void createBooking_validFlight_returns201() throws Exception {
        when(bookingService.create(any(), eq(IDEMPOTENCY_KEY)))
                .thenReturn(new Booking().id(UUID.randomUUID()).employeeId("EMP9876").destination("NYC"));

        mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validFlightBooking())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeId").value("EMP9876"));
    }

    @Test
    void createBooking_hotelWithoutReturnDate_returns422() throws Exception {
        final Booking request = validFlightBooking()
                .resourceType(ResourceType.HOTEL)
                .returnDate(null);

        mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reasonCode").value("INVALID_BOOKING"));
    }

    // ─────────────────────────────── GET /bookings ────────────────────────────────

    @Test
    void listBookings_validEmployeeId_returns200WithPagedResponse() throws Exception {
        final BookingEntity entity = BookingEntity.builder().id(UUID.randomUUID()).employeeId("EMP9876").build();
        final Page<BookingEntity> page = new PageImpl<>(List.of(entity), Pageable.ofSize(20), 1);

        when(bookingService.findByEmployeeId(eq("EMP9876"), any())).thenReturn(page);
        when(bookingMapper.toDto(entity)).thenReturn(new Booking().id(entity.getId()).employeeId("EMP9876"));

        mockMvc.perform(get("/bookings").param("employeeId", "EMP9876"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.bookings[0].employeeId").value("EMP9876"));
    }

    @Test
    void listBookings_missingEmployeeId_returns400() throws Exception {
        mockMvc.perform(get("/bookings"))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────── GET /bookings/{id} ───────────────────────────────

    @Test
    void getBooking_existingId_returns200WithBooking() throws Exception {
        final UUID bookingId = UUID.randomUUID();
        final BookingEntity entity = BookingEntity.builder().id(bookingId).employeeId("EMP9876").build();

        when(bookingService.findById(bookingId)).thenReturn(entity);
        when(bookingMapper.toDto(entity)).thenReturn(
                new Booking().id(bookingId).employeeId("EMP9876").destination("NYC"));

        mockMvc.perform(get("/bookings/{bookingId}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.employeeId").value("EMP9876"));
    }

    @Test
    void getBooking_unknownId_returns404() throws Exception {
        final UUID bookingId = UUID.randomUUID();
        when(bookingService.findById(bookingId)).thenThrow(new BookingNotFoundException(bookingId));

        mockMvc.perform(get("/bookings/{bookingId}", bookingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reasonCode").value("BOOKING_NOT_FOUND"));
    }

    // ─────────────────── POST /bookings/{id}/cancel ───────────────────────────────

    @Test
    void cancelBooking_existingId_returns200WithCancelledBooking() throws Exception {
        final UUID bookingId = UUID.randomUUID();
        final BookingEntity entity = BookingEntity.builder().id(bookingId).employeeId("EMP9876").build();

        when(bookingService.cancelByUser(eq(bookingId), anyString())).thenReturn(entity);
        when(bookingMapper.toDto(entity)).thenReturn(
                new Booking().id(bookingId).employeeId("EMP9876")
                        .status(com.aniri.workflow_service.web.model.BookingStatus.CANCELLED));

        mockMvc.perform(post("/bookings/{bookingId}/cancel", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelBooking_unknownId_returns404() throws Exception {
        final UUID bookingId = UUID.randomUUID();
        when(bookingService.cancelByUser(eq(bookingId), anyString()))
                .thenThrow(new BookingNotFoundException(bookingId));

        mockMvc.perform(post("/bookings/{bookingId}/cancel", bookingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reasonCode").value("BOOKING_NOT_FOUND"));
    }

    // ───────────────────────── GET /bookings/search ───────────────────────────────

    @Test
    void searchBookingOptions_validQuery_returns200WithList() throws Exception {
        when(searchService.search(any()))
                .thenReturn(List.of(new BookingSearch().providerId("BA-001").destination("NYC")));

        mockMvc.perform(get("/bookings/search")
                        .param("resourceType", "FLIGHT")
                        .param("destination", "NYC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].providerId").value("BA-001"));
    }

    @Test
    void searchBookingOptions_missingDestination_returns400() throws Exception {
        mockMvc.perform(get("/bookings/search").param("resourceType", "FLIGHT"))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────── POST /bookings/{bookingId}/appointments ──────────────────

    @Test
    void createAppointment_validRequest_returns201() throws Exception {
        final UUID bookingId = UUID.randomUUID();
        when(appointmentService.create(any()))
                .thenReturn(new Appointment().id(UUID.randomUUID()).bookingId(bookingId).appointmentType(AppointmentType.SPA));

        final Appointment request = new Appointment()
                .appointmentType(AppointmentType.SPA)
                .scheduledAt(OffsetDateTime.parse("2027-11-06T10:00:00Z"));

        mockMvc.perform(post("/bookings/{bookingId}/appointments", bookingId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentType").value("SPA"));
    }

    @Test
    void createAppointment_bookingNotFound_returns404() throws Exception {
        final UUID bookingId = UUID.randomUUID();
        when(appointmentService.create(any())).thenThrow(new BookingNotFoundException(bookingId));

        final Appointment request = new Appointment()
                .appointmentType(AppointmentType.SPA)
                .scheduledAt(OffsetDateTime.parse("2027-11-06T10:00:00Z"));

        mockMvc.perform(post("/bookings/{bookingId}/appointments", bookingId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reasonCode").value("BOOKING_NOT_FOUND"));
    }

    // ─────────────────────────────── helpers ──────────────────────────────────────

    private static Booking validFlightBooking() {
        return new Booking()
                .employeeId("EMP9876")
                .resourceType(ResourceType.FLIGHT)
                .destination("NYC")
                .departureDate(OffsetDateTime.parse("2027-11-05T08:00:00Z"))
                .returnDate(OffsetDateTime.parse("2027-11-08T18:00:00Z"))
                .travelerCount(1)
                .costCenterRef("CC-456");
    }
}
