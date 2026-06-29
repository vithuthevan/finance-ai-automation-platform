package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.Expense;
import com.finance.platform.finance.domain.model.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExpenseJpaRepository extends JpaRepository<Expense, UUID> {

	Page<Expense> findByClientId(UUID clientId, Pageable pageable);

	Page<Expense> findByClientIdAndStatus(UUID clientId, TransactionStatus status, Pageable pageable);

	Optional<Expense> findByIdAndClientId(UUID id, UUID clientId);
}
