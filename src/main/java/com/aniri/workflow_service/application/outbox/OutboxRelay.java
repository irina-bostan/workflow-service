package com.aniri.workflow_service.application.outbox;

import com.aniri.workflow_service.domain.booking.BookingEventPublisher;
import com.aniri.workflow_service.domain.booking.model.BookingCreatedEvent;
import com.aniri.workflow_service.domain.outbox.OutboxEntry;
import com.aniri.workflow_service.domain.outbox.OutboxRepository;
import com.aniri.workflow_service.domain.outbox.OutboxStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Polls the outbox for PENDING events and publishes them via the configured
 * {@link BookingEventPublisher}. Safe to run on every ECS task — the claim query uses
 * {@code FOR UPDATE SKIP LOCKED} so concurrent relays each see a disjoint batch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private static final String EVENT_TYPE_BOOKING_CREATED = "BookingCreatedEvent";
    private static final int CLAIM_BATCH_SIZE = 100;
    private static final long SENT_RETENTION_HOURS = 24;

    private final OutboxRepository outboxRepository;
    private final BookingEventPublisher publisher;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:2000}")
    @Transactional
    public void publishPending() {
        final List<OutboxEntry> pending = outboxRepository.claimPending(CLAIM_BATCH_SIZE);
        if (pending.isEmpty()) return;

        log.debug("Outbox relay processing {} pending entries", pending.size());
        pending.forEach(this::publishOne);
    }

    private void publishOne(final OutboxEntry entry) {
        try {
            dispatch(entry);
            entry.markSent();
        } catch (final Exception e) {
            log.warn("Outbox publish failed entry={} attempt={}: {}",
                    entry.getId(), entry.getAttempts() + 1, e.getMessage());
            entry.recordFailure(e.getMessage());
        }
    }

    private void dispatch(final OutboxEntry entry) throws Exception {
        if (EVENT_TYPE_BOOKING_CREATED.equals(entry.getEventType())) {
            final BookingCreatedEvent event = objectMapper.readValue(entry.getPayload(), BookingCreatedEvent.class);
            publisher.publishBookingCreated(event);
            return;
        }
        throw new IllegalStateException("Unknown event_type: " + entry.getEventType());
    }

    /**
     * Hourly cleanup of SENT rows older than 24 h. At 100 rps the table accrues ~8.6M rows/day;
     * unbounded growth degrades autovacuum, snapshot/restore, and any non-partial-index scans.
     * Runs on every ECS task — concurrent DELETEs are idempotent under MVCC; lock contention
     * is negligible at this scale.
     */
    @Scheduled(fixedDelayString = "${outbox.cleanup.fixed-delay-ms:3600000}")
    @Transactional
    public void purgeSent() {
        final OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC)
                .minus(SENT_RETENTION_HOURS, ChronoUnit.HOURS);
        final int deleted = outboxRepository.deleteSentBefore(cutoff);
        if (deleted > 0) {
            log.info("Outbox purge removed {} sent rows older than {}", deleted, cutoff);
        }
    }
}
