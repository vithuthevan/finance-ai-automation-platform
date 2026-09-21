package com.finance.platform.core.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventOutboxJpaRepository extends JpaRepository<EventOutbox, UUID> {

	@Query("""
			select e from EventOutbox e
			where e.status = com.finance.platform.core.outbox.EventOutbox$Status.PENDING
			  and e.nextAttemptAt <= :now
			  and (e.lockedUntil is null or e.lockedUntil < :now)
			order by e.nextAttemptAt asc
			""")
	List<EventOutbox> findPendingDue(@Param("now") Instant now);

	Optional<EventOutbox> findFirstByEventTypeAndAggregateIdAndStatus(
			String eventType, UUID aggregateId, EventOutbox.Status status);

	@Modifying
	@Query(value = """
			WITH picked AS (
			    SELECT id FROM event_outbox
			    WHERE status = 'PENDING'
			      AND next_attempt_at <= :now
			      AND (locked_until IS NULL OR locked_until < :now)
			    ORDER BY next_attempt_at ASC
			    FOR UPDATE SKIP LOCKED
			    LIMIT :batchSize
			)
			UPDATE event_outbox e
			SET locked_until = :lockedUntil, locked_by = :workerId
			FROM picked
			WHERE e.id = picked.id
			RETURNING e.id
			""", nativeQuery = true)
	List<UUID> claimBatch(
			@Param("now") Instant now,
			@Param("lockedUntil") Instant lockedUntil,
			@Param("workerId") String workerId,
			@Param("batchSize") int batchSize);
}
