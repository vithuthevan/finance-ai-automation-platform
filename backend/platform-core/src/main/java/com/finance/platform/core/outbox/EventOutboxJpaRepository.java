package com.finance.platform.core.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
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
			  and e.createdAt <= :createdBefore
			order by e.createdAt
			""")
	List<EventOutbox> findPendingDue(@Param("createdBefore") Instant createdBefore);

	Optional<EventOutbox> findFirstByEventTypeAndAggregateIdAndStatus(
			String eventType, UUID aggregateId, EventOutbox.Status status);
}
