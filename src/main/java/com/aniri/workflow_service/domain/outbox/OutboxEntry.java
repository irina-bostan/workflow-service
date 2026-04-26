package com.aniri.workflow_service.domain.outbox;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "outbox")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.lastAttemptAt = this.sentAt;
        this.attempts++;
    }

    public void recordFailure(String message) {
        this.attempts++;
        this.lastAttemptAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.lastError = message;
        if (this.attempts >= 10) {
            this.status = OutboxStatus.FAILED;
        }
    }
}
