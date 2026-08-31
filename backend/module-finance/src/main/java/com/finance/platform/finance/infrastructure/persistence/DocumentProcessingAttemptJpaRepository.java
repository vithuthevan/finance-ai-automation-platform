package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.DocumentProcessingAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface DocumentProcessingAttemptJpaRepository extends JpaRepository<DocumentProcessingAttempt, UUID> {

	long countByReceipt_Id(UUID receiptId);

	long countByReceipt_IdAndCreatedAtAfter(UUID receiptId, Instant since);

	@Query("select coalesce(avg(a.durationMs), 0) from DocumentProcessingAttempt a where a.firmId = :firmId and a.durationMs is not null")
	double averageDurationMs(@Param("firmId") UUID firmId);

	long countByFirmIdAndStatus(UUID firmId, String status);
}
