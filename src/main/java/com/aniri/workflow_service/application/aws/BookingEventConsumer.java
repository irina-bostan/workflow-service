package com.aniri.workflow_service.application.aws;

import com.aniri.workflow_service.application.properties.AwsProperties;
import com.aniri.workflow_service.domain.booking.BookingProvider;
import com.aniri.workflow_service.domain.booking.BookingService;
import com.aniri.workflow_service.domain.booking.exception.BookingProviderRejectionException;
import com.aniri.workflow_service.domain.booking.model.BookingCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;

/**
 * Closes the booking workflow loop: pulls {@code BookingCreatedEvent} messages produced
 * by {@link com.aniri.workflow_service.application.outbox.OutboxRelay}, calls the
 * {@link BookingProvider} (Duffel mock) to reserve upstream, and updates the booking row
 * to CONFIRMED or CANCELLED. A second poller drains the DLQ and marks any booking that
 * exceeded the SQS redrive limit as CANCELLED so it doesn't sit in PENDING forever.
 *
 * <p>Idempotent under SQS at-least-once: the service-level confirm/cancel methods skip
 * if the booking is no longer PENDING.
 */
@Component
@ConditionalOnProperty(name = "aws.sqs.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class BookingEventConsumer {

    private static final int MAX_MESSAGES = 10;
    private static final int WAIT_TIME_SECONDS = 20;
    private static final String DLQ_CANCELLATION_REASON = "Redelivery limit exceeded";

    private final SqsAsyncClient sqs;
    private final ObjectMapper objectMapper;
    private final BookingProvider bookingProvider;
    private final BookingService bookingService;
    private final AwsProperties awsProperties;

    @Scheduled(fixedDelayString = "${booking-events.consumer.fixed-delay-ms:1000}")
    @Observed(name = "booking.consumer.poll", contextualName = "BookingEventConsumer#pollMain")
    public void pollMain() {
        final String queueUrl = awsProperties.sqs().bookingEventsQueueUrl();
        for (final Message message : receive(queueUrl)) {
            try {
                handleMain(message);
                delete(queueUrl, message);
            } catch (final Exception e) {
                log.warn("Transient failure for booking event {}: {}", message.messageId(), e.getMessage());
                // Leave message un-deleted; SQS will redeliver after visibility timeout.
                // After maxReceiveCount the message is moved to the DLQ.
            }
        }
    }

    @Scheduled(fixedDelayString = "${booking-events.dlq.fixed-delay-ms:5000}")
    @Observed(name = "booking.consumer.poll.dlq", contextualName = "BookingEventConsumer#pollDlq")
    public void pollDlq() {
        final String dlqUrl = awsProperties.sqs().dlqUrl();
        if (dlqUrl == null || dlqUrl.isBlank()) return;

        for (final Message message : receive(dlqUrl)) {
            try {
                handleDlq(message);
            } catch (final Exception e) {
                log.error("DLQ handler error for {}: {}", message.messageId(), e.getMessage(), e);
            }
            // Always delete from DLQ — the booking is now in a terminal state, retrying
            // upstream wouldn't recover the situation.
            delete(dlqUrl, message);
        }
    }

    private void handleMain(final Message message) throws Exception {
        final BookingCreatedEvent event = objectMapper.readValue(message.body(), BookingCreatedEvent.class);
        try {
            final String providerRef = bookingProvider.reserve(event);
            bookingService.confirm(event.bookingId(), providerRef);
        } catch (final BookingProviderRejectionException e) {
            // Terminal rejection: cancel the booking and let the message be deleted —
            // retrying with the same upstream input will hit the same rejection.
            bookingService.cancel(event.bookingId(), e.getMessage());
        }
    }

    private void handleDlq(final Message message) throws Exception {
        final BookingCreatedEvent event = objectMapper.readValue(message.body(), BookingCreatedEvent.class);
        bookingService.cancel(event.bookingId(), DLQ_CANCELLATION_REASON);
    }

    private List<Message> receive(final String queueUrl) {
        try {
            final ReceiveMessageResponse response = sqs.receiveMessage(ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(MAX_MESSAGES)
                            .waitTimeSeconds(WAIT_TIME_SECONDS)
                            .build())
                    .join();
            return response.messages();
        } catch (final Exception e) {
            log.warn("SQS receive failed on {}: {}", queueUrl, e.getMessage());
            return List.of();
        }
    }

    private void delete(final String queueUrl, final Message message) {
        sqs.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build())
                .join();
    }
}
