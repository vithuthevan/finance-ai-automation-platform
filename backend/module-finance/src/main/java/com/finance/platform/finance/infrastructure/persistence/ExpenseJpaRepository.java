package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.Expense;
import com.finance.platform.finance.domain.model.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ExpenseJpaRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

	Page<Expense> findByClientId(UUID clientId, Pageable pageable);

	Page<Expense> findByClientIdAndStatus(UUID clientId, TransactionStatus status, Pageable pageable);

	Optional<Expense> findByIdAndClientId(UUID id, UUID clientId);

	Optional<Expense> findByIdAndClient_IdAndFirmId(UUID id, UUID clientId, UUID firmId);

	@org.springframework.data.jpa.repository.Query("select count(e) from Expense e join e.receipts r where r.id = :receiptId")
	long countByReceiptId(@org.springframework.data.repository.query.Param("receiptId") UUID receiptId);

	@org.springframework.data.jpa.repository.Query("select count(e) > 0 from Expense e join e.receipts r where r.id = :receiptId and e.status in :statuses")
	boolean existsByReceiptIdAndStatusIn(
			@org.springframework.data.repository.query.Param("receiptId") UUID receiptId,
			@org.springframework.data.repository.query.Param("statuses") java.util.Collection<com.finance.platform.finance.domain.model.TransactionStatus> statuses);

	Optional<Expense> findFirstByFirmIdAndClient_IdAndStatusAndVendorNameIgnoreCaseOrderByApprovedAtDesc(
			UUID firmId, UUID clientId, TransactionStatus status, String vendorName);

	long countByClientIdAndStatus(UUID clientId, TransactionStatus status);

	java.util.List<Expense> findByClientIdAndStatusAndTransactionDateBetween(
			UUID clientId, TransactionStatus status, java.time.LocalDate from, java.time.LocalDate to);

	@org.springframework.data.jpa.repository.Query("""
			select e from Expense e
			left join fetch e.category
			where e.firmId = :firmId
			  and e.client.id = :clientId
			  and e.status = com.finance.platform.finance.domain.model.TransactionStatus.APPROVED
			  and e.transactionDate between :from and :to
			  and (:categoryId is null or e.category.id = :categoryId)
			order by e.transactionDate, e.id
			""")
	java.util.List<Expense> findApprovedForReport(
			@org.springframework.data.repository.query.Param("firmId") UUID firmId,
			@org.springframework.data.repository.query.Param("clientId") UUID clientId,
			@org.springframework.data.repository.query.Param("from") java.time.LocalDate from,
			@org.springframework.data.repository.query.Param("to") java.time.LocalDate to,
			@org.springframework.data.repository.query.Param("categoryId") UUID categoryId);
}
