package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.Income;
import com.finance.platform.finance.domain.model.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IncomeJpaRepository extends JpaRepository<Income, UUID> {

	Page<Income> findByClientId(UUID clientId, Pageable pageable);

	Page<Income> findByClientIdAndStatus(UUID clientId, TransactionStatus status, Pageable pageable);

	Optional<Income> findByIdAndClientId(UUID id, UUID clientId);
}
