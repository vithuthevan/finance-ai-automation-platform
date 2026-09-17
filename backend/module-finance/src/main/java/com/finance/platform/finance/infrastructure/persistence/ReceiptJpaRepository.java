package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.Receipt;
import com.finance.platform.finance.domain.model.Receipt.DocumentType;
import com.finance.platform.finance.domain.model.Receipt.ReceiptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptJpaRepository extends JpaRepository<Receipt, UUID>, JpaSpecificationExecutor<Receipt> {

	@Query("select r from Receipt r left join fetch r.client left join fetch r.suggestedCategory where r.id = :id")
	Optional<Receipt> findDetailedById(@Param("id") UUID id);

	Optional<Receipt> findByIdAndClientIdAndDeletedAtIsNull(UUID id, UUID clientId);

	Optional<Receipt> findByIdAndClient_IdAndFirmIdAndDeletedAtIsNull(UUID id, UUID clientId, UUID firmId);

	Optional<Receipt> findFirstByClientIdAndChecksumSha256AndDeletedAtIsNull(UUID clientId, String checksumSha256);

	Optional<Receipt> findFirstByFirmIdAndClient_IdAndChecksumSha256AndDeletedAtIsNull(
			UUID firmId, UUID clientId, String checksumSha256);

	Page<Receipt> findByClientIdAndDeletedAtIsNull(UUID clientId, Pageable pageable);

	Page<Receipt> findByClientIdAndStatusAndDeletedAtIsNull(UUID clientId, ReceiptStatus status, Pageable pageable);

	Page<Receipt> findByFirmIdAndDeletedAtIsNull(UUID firmId, Pageable pageable);

	List<Receipt> findByClientIdAndDocumentTypeAndDeletedAtIsNull(UUID clientId, DocumentType documentType);

	long countByClientIdAndStatusAndDeletedAtIsNull(UUID clientId, ReceiptStatus status);

	long countByClientIdAndStatusInAndDeletedAtIsNull(UUID clientId, List<ReceiptStatus> statuses);

	long countByClientIdAndUploadedAtGreaterThanEqualAndDeletedAtIsNull(UUID clientId, Instant uploadedAt);

	long countByFirmIdAndAiMetadata_ReviewOutcome(UUID firmId, String reviewOutcome);

	long countByDeletedAtIsNull();

	long countByStatusAndDeletedAtIsNull(ReceiptStatus status);
}
