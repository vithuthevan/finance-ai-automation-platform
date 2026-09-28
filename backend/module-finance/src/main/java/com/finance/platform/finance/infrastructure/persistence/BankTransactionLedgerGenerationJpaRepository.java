package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.BankTransactionLedgerGeneration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BankTransactionLedgerGenerationJpaRepository extends JpaRepository<BankTransactionLedgerGeneration, UUID> {

	@Query("""
			select g from BankTransactionLedgerGeneration g
			left join fetch g.expense
			left join fetch g.income
			left join fetch g.bankTransaction
			where g.bankTransaction.id = :bankTransactionId
			""")
	Optional<BankTransactionLedgerGeneration> findFetchedByBankTransaction_Id(
			@Param("bankTransactionId") UUID bankTransactionId);
}
