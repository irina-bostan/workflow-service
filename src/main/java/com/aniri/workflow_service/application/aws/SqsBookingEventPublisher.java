package com.aniri.workflow_service.application.aws;

import com.aniri.workflow_service.application.properties.AwsProperties;
import com.aniri.workflow_service.domain.booking.BookingEventPublisher;
import com.aniri.workflow_service.domain.booking.model.BookingCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@Service
@ConditionalOnProperty(name = "aws.sqs.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SqsBookingEventPublisher implements BookingEventPublisher {

    private final SqsAsyncClient sqsAsyncClient;
    private final ObjectMapper objectMapper;
    private final AwsProperties awsProperties;

    /**
     * Synchronous: throws on send failure so the outbox relay leaves the row PENDING.
     */
    @Override
    public void publishBookingCreated(final BookingCreatedEvent event) {
        final String body = serialize(event);
        final SendMessageResponse resp = sqsAsyncClient.sendMessage(SendMessageRequest.builder()
                        .queueUrl(awsProperties.sqs().bookingEventsQueueUrl())
                        .messageBody(body)
                        .messageGroupId(event.employeeId())
                        .messageDeduplicationId(event.bookingId().toString())
                        .build())
                .join();
        log.info("Published BookingCreatedEvent bookingId={} sqsMessageId={}",
                event.bookingId(), resp.messageId());
    }

    private String serialize(final BookingCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (final JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize BookingCreatedEvent", e);
        }
    }
}
