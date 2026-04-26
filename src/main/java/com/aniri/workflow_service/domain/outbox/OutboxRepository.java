package com.aniri.workflow_service.domain.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {

    /**
     * Claims a batch of pending rows with a row-level lock that's invisible to concurrent
     * relay instances. Multiple ECS tasks running this query simultaneously each see a
     * disjoint set of rows ({@code SKIP LOCKED}), so events are never published twice.
     * Locks release at transaction commit.
     */
    @Query(value = """
            SELECT * FROM outbox
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEntry> claimPending(@Param("limit") int limit);

    /**
     * Bounds outbox table growth at peak. At 100 rps the table accrues ~8.6M rows/day; without
     * a retention window vacuum can't keep up, the heap grows unboundedly, and snapshot/restore
     * times degrade. Returns rows deleted for observability.
     */
    @Modifying
    @Query("DELETE FROM OutboxEntry o WHERE o.status = com.aniri.workflow_service.domain.outbox.OutboxStatus.SENT AND o.sentAt < :cutoff")
    int deleteSentBefore(@Param("cutoff") OffsetDateTime cutoff);
}
