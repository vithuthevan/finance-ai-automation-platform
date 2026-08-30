package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.BankTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankTransactionJpaRepository extends JpaRepository<BankTransaction, UUID> {

	Page<BankTransaction> findByClientId(UUID clientId, Pageable pageable);

	Page<BankTransaction> findByClientIdAndMatchStatus(UUID clientId, BankTransaction.MatchStatus status, Pageable pageable);

	Optional<BankTransaction> findByIdAndClientId(UUID id, UUID clientId);

	long countByClientIdAndMatchStatus(UUID clientId, BankTransaction.MatchStatus status);

	List<BankTransaction> findByClientIdAndTxnDateBetween(UUID clientId, LocalDate from, LocalDate to);
}
