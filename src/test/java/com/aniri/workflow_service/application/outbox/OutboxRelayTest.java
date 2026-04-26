package com.aniri.workflow_service.application.outbox;

import com.aniri.workflow_service.domain.booking.BookingEventPublisher;
import com.aniri.workflow_service.domain.booking.model.BookingCreatedEvent;
import com.aniri.workflow_service.domain.outbox.OutboxEntry;
import com.aniri.workflow_service.domain.outbox.OutboxRepository;
import com.aniri.workflow_service.domain.outbox.OutboxStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock private OutboxRepository outboxRepository;
    @Mock private BookingEventPublisher publisher;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private OutboxRelay relay;

    @Test
    void publishPending_publishSucceeds_marksEntrySent() throws Exception {
        final OutboxEntry entry = pendingEntry();
        final BookingCreatedEvent event = BookingCreatedEvent.builder().bookingId(UUID.randomUUID()).build();

        when(outboxRepository.claimPending(anyInt()))
                .thenReturn(List.of(entry));
        when(objectMapper.readValue(eq("{}"), eq(BookingCreatedEvent.class))).thenReturn(event);

        relay.publishPending();

        verify(publisher).publishBookingCreated(event);
        assertThat(entry.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(entry.getAttempts()).isEqualTo(1);
        assertThat(entry.getSentAt()).isNotNull();
    }

    @Test
    void publishPending_publishThrows_recordsFailureAndLeavesPending() throws Exception {
        final OutboxEntry entry = pendingEntry();

        when(outboxRepository.claimPending(anyInt()))
                .thenReturn(List.of(entry));
        when(objectMapper.readValue(eq("{}"), eq(BookingCreatedEvent.class)))
                .thenReturn(BookingCreatedEvent.builder().build());
        doThrow(new RuntimeException("SQS unreachable"))
                .when(publisher).publishBookingCreated(any());

        relay.publishPending();

        assertThat(entry.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(entry.getAttempts()).isEqualTo(1);
        assertThat(entry.getLastError()).contains("SQS unreachable");
    }

    private static OutboxEntry pendingEntry() {
        return OutboxEntry.builder()
                .id(UUID.randomUUID())
                .aggregateType("BOOKING")
                .aggregateId(UUID.randomUUID())
                .eventType("BookingCreatedEvent")
                .payload("{}")
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .build();
    }
}
