package com.aniri.workflow_service.application.aws;

import com.aniri.workflow_service.application.properties.AwsProperties;
import com.aniri.workflow_service.domain.booking.BookingProvider;
import com.aniri.workflow_service.domain.booking.BookingService;
import com.aniri.workflow_service.domain.booking.exception.BookingProviderRejectionException;
import com.aniri.workflow_service.domain.booking.model.BookingCreatedEvent;
import com.aniri.workflow_service.domain.booking.model.ResourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingEventConsumerTest {

    private static final String MAIN_QUEUE = "https://sqs/main";
    private static final String DLQ = "https://sqs/dlq";

    @Mock private SqsAsyncClient sqs;
    @Mock private BookingProvider bookingProvider;
    @Mock private BookingService bookingService;
    @Mock private AwsProperties awsProperties;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks private BookingEventConsumer consumer;

    @BeforeEach
    void wireProperties() {
        // Constructor injection includes ObjectMapper, but Mockito can't inject the real one;
        // re-instantiate the consumer with the real ObjectMapper instead of the @InjectMocks one.
        consumer = new BookingEventConsumer(sqs, objectMapper, bookingProvider, bookingService, awsProperties);
    }

    @Test
    void pollMain_validEvent_reservesConfirmsAndDeletes() throws Exception {
        final UUID bookingId = UUID.randomUUID();
        final Message message = messageFor(bookingId);

        when(awsProperties.sqs()).thenReturn(new AwsProperties.Sqs(true, MAIN_QUEUE, DLQ));
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        ReceiveMessageResponse.builder().messages(message).build()));
        when(sqs.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()));
        when(bookingProvider.reserve(any())).thenReturn("DUFFEL-MOCK-ABCD1234");

        consumer.pollMain();

        verify(bookingService).confirm(bookingId, "DUFFEL-MOCK-ABCD1234");
        verify(bookingService, never()).cancel(any(), any());
        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void pollMain_providerRejects_cancelsAndDeletes() throws Exception {
        final UUID bookingId = UUID.randomUUID();
        final Message message = messageFor(bookingId);

        when(awsProperties.sqs()).thenReturn(new AwsProperties.Sqs(true, MAIN_QUEUE, DLQ));
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        ReceiveMessageResponse.builder().messages(message).build()));
        when(sqs.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()));
        when(bookingProvider.reserve(any()))
                .thenThrow(new BookingProviderRejectionException("no availability"));

        consumer.pollMain();

        verify(bookingService).cancel(bookingId, "no availability");
        verify(bookingService, never()).confirm(any(), any());
        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void pollMain_transientError_leavesMessageForRedelivery() throws Exception {
        final UUID bookingId = UUID.randomUUID();
        final Message message = messageFor(bookingId);

        when(awsProperties.sqs()).thenReturn(new AwsProperties.Sqs(true, MAIN_QUEUE, DLQ));
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        ReceiveMessageResponse.builder().messages(message).build()));
        when(bookingProvider.reserve(any())).thenReturn("DUFFEL-MOCK-ABCD1234");
        // Simulate a DB failure during confirm — neither confirm nor cancel completes.
        org.mockito.Mockito.doThrow(new RuntimeException("DB unavailable"))
                .when(bookingService).confirm(any(), any());

        consumer.pollMain();

        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void pollDlq_validEvent_cancelsAndDeletes() throws Exception {
        final UUID bookingId = UUID.randomUUID();
        final Message message = messageFor(bookingId);

        when(awsProperties.sqs()).thenReturn(new AwsProperties.Sqs(true, MAIN_QUEUE, DLQ));
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        ReceiveMessageResponse.builder().messages(message).build()));
        when(sqs.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()));

        consumer.pollDlq();

        verify(bookingService).cancel(bookingId, "Redelivery limit exceeded");
        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void pollDlq_noDlqUrl_skipsReceive() {
        when(awsProperties.sqs()).thenReturn(new AwsProperties.Sqs(true, MAIN_QUEUE, null));

        consumer.pollDlq();

        verify(sqs, never()).receiveMessage(any(ReceiveMessageRequest.class));
    }

    private Message messageFor(final UUID bookingId) throws Exception {
        final BookingCreatedEvent event = BookingCreatedEvent.builder()
                .bookingId(bookingId)
                .employeeId("EMP9876")
                .resourceType(ResourceType.FLIGHT)
                .destination("NYC")
                .departureDate(OffsetDateTime.parse("2027-11-05T08:00:00Z"))
                .build();
        return Message.builder()
                .messageId(UUID.randomUUID().toString())
                .receiptHandle("rh-" + bookingId)
                .body(objectMapper.writeValueAsString(event))
                .build();
    }
}
