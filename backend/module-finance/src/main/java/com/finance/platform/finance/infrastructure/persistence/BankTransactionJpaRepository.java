package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.BankTransaction;
import com.finance.platform.finance.domain.model.ReconciliationMatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankTransactionJpaRepository extends JpaRepository<BankTransaction, UUID> {

	@Query("""
			select t from BankTransaction t
			where t.client.id = :clientId
			""")
	Page<BankTransaction> findByClient_Id(@Param("clientId") UUID clientId, Pageable pageable);

	@Query("""
			select t from BankTransaction t
			where t.client.id = :clientId and t.matchStatus = :status
			""")
	Page<BankTransaction> findByClient_IdAndMatchStatus(
			@Param("clientId") UUID clientId,
			@Param("status") BankTransaction.MatchStatus status,
			Pageable pageable);

	@Query("""
			select count(t) from BankTransaction t
			where t.client.id = :clientId and t.matchStatus = :status
			""")
	long countByClient_IdAndMatchStatus(@Param("clientId") UUID clientId, @Param("status") BankTransaction.MatchStatus status);

	Optional<BankTransaction> findByIdAndClient_Id(UUID id, UUID clientId);

	Optional<BankTransaction> findByIdAndClient_IdAndFirmId(UUID id, UUID clientId, UUID firmId);

	@Query("""
			select count(t) from BankTransaction t
			where t.client.id = :clientId
			  and t.bankAccount.id = :bankAccountId
			  and t.txnDate between :from and :to
			  and t.matchStatus not in ('IGNORED', 'MATCHED')
			""")
	long countActionableUnmatched(
			@Param("clientId") UUID clientId,
			@Param("bankAccountId") UUID bankAccountId,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to);

	@Query("""
			select count(t) from BankTransaction t
			where t.client.id = :clientId
			  and t.txnDate between :from and :to
			  and t.matchStatus = :status
			""")
	long countByClientAndPeriodAndStatus(
			@Param("clientId") UUID clientId,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to,
			@Param("status") BankTransaction.MatchStatus status);

	boolean existsByBankAccount_IdAndExternalRowHash(UUID bankAccountId, String externalRowHash);

	@Query("""
			select t from BankTransaction t
			where t.client.id = :clientId
			  and (:bankAccountId is null or t.bankAccount.id = :bankAccountId)
			  and (:status is null or t.matchStatus = :status)
			  and (:from is null or t.txnDate >= :from)
			  and (:to is null or t.txnDate <= :to)
			""")
	Page<BankTransaction> searchFiltered(
			@Param("clientId") UUID clientId,
			@Param("bankAccountId") UUID bankAccountId,
			@Param("status") BankTransaction.MatchStatus status,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to,
			Pageable pageable);

	@Query("""
			select t from BankTransaction t
			where t.client.id = :clientId
			  and (:bankAccountId is null or t.bankAccount.id = :bankAccountId)
			  and (:status is null or t.matchStatus = :status)
			  and (:from is null or t.txnDate >= :from)
			  and (:to is null or t.txnDate <= :to)
			  and (lower(coalesce(t.description, '')) like lower(concat('%', :q, '%'))
			       or lower(coalesce(t.referenceNo, '')) like lower(concat('%', :q, '%')))
			""")
	Page<BankTransaction> searchWithText(
			@Param("clientId") UUID clientId,
			@Param("bankAccountId") UUID bankAccountId,
			@Param("status") BankTransaction.MatchStatus status,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to,
			@Param("q") String q,
			Pageable pageable);

	@Query("""
			select t from BankTransaction t
			where t.client.id = :clientId and t.txnDate between :from and :to
			""")
	List<BankTransaction> findByClient_IdAndTxnDateBetween(
			@Param("clientId") UUID clientId,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to);

	@Query("""
			select count(t) from BankTransaction t
			where t.client.id = :clientId
			  and t.txnDate between :from and :to
			""")
	long countInPeriod(@Param("clientId") UUID clientId, @Param("from") LocalDate from, @Param("to") LocalDate to);

	@Query("""
			select count(t) from BankTransaction t
			where t.client.id = :clientId
			  and t.bankAccount is not null
			  and t.txnDate between :from and :to
			""")
	long countImportedInPeriod(@Param("clientId") UUID clientId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
