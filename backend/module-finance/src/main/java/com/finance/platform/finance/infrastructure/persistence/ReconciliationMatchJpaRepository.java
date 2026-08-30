package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.ReconciliationMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconciliationMatchJpaRepository extends JpaRepository<ReconciliationMatch, UUID> {

	List<ReconciliationMatch> findByBankTransaction_Id(UUID bankTransactionId);
}
