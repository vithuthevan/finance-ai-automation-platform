package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.ReconciliationMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationMatchJpaRepository extends JpaRepository<ReconciliationMatch, UUID> {

	List<ReconciliationMatch> findByBankTransaction_Id(UUID bankTransactionId);

	Optional<ReconciliationMatch> findFirstByBankTransaction_IdAndStatusOrderByCreatedAtDesc(
			UUID bankTransactionId, ReconciliationMatch.MatchStatus status);

	@Query("""
			select m from ReconciliationMatch m
			where m.bankTransaction.id = :bankTransactionId
			  and m.status = com.finance.platform.finance.domain.model.ReconciliationMatch$MatchStatus.SUGGESTED
			order by m.matchScore desc nulls last, m.createdAt desc
			""")
	List<ReconciliationMatch> findActiveSuggestions(@Param("bankTransactionId") UUID bankTransactionId);

	@Query("""
			select count(m) > 0 from ReconciliationMatch m
			where m.status = com.finance.platform.finance.domain.model.ReconciliationMatch$MatchStatus.CONFIRMED
			  and ((m.expense.id = :expenseId and :expenseId is not null)
			    or (m.income.id = :incomeId and :incomeId is not null))
			""")
	boolean isLedgerEntryMatched(@Param("expenseId") UUID expenseId, @Param("incomeId") UUID incomeId);
}
