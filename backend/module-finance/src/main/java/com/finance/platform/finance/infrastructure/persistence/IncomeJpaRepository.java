package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.Income;
import com.finance.platform.finance.domain.model.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface IncomeJpaRepository extends JpaRepository<Income, UUID>, JpaSpecificationExecutor<Income> {

	Page<Income> findByClientId(UUID clientId, Pageable pageable);

	Page<Income> findByClientIdAndStatus(UUID clientId, TransactionStatus status, Pageable pageable);

	Optional<Income> findByIdAndClientId(UUID id, UUID clientId);

	Optional<Income> findByIdAndClient_IdAndFirmId(UUID id, UUID clientId, UUID firmId);

	@org.springframework.data.jpa.repository.Query("select count(i) from Income i join i.receipts r where r.id = :receiptId")
	long countByReceiptId(@org.springframework.data.repository.query.Param("receiptId") UUID receiptId);

	@org.springframework.data.jpa.repository.Query("select count(i) > 0 from Income i join i.receipts r where r.id = :receiptId and i.status in :statuses")
	boolean existsByReceiptIdAndStatusIn(
			@org.springframework.data.repository.query.Param("receiptId") UUID receiptId,
			@org.springframework.data.repository.query.Param("statuses") java.util.Collection<TransactionStatus> statuses);

	Optional<Income> findFirstByFirmIdAndClient_IdAndStatusAndCustomerNameIgnoreCaseOrderByApprovedAtDesc(
			UUID firmId, UUID clientId, TransactionStatus status, String customerName);

	long countByClientIdAndStatus(UUID clientId, TransactionStatus status);

	java.util.List<Income> findByClientIdAndStatusAndTransactionDateBetween(
			UUID clientId, TransactionStatus status, java.time.LocalDate from, java.time.LocalDate to);
}
