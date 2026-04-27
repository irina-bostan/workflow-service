package com.aniri.workflow_service.application.aws;

import com.aniri.workflow_service.domain.booking.BookingEventPublisher;
import com.aniri.workflow_service.domain.booking.model.BookingCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Active when {@code aws.sqs.enabled=false} (or unset). Keeps the port satisfied in dev.
 */
@Service
@ConditionalOnProperty(name = "aws.sqs.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class NoOpBookingEventPublisher implements BookingEventPublisher {

    @Override
    public void publishBookingCreated(final BookingCreatedEvent event) {
        log.debug("SQS disabled — skipping BookingCreatedEvent bookingId={}", event.bookingId());
    }
}
