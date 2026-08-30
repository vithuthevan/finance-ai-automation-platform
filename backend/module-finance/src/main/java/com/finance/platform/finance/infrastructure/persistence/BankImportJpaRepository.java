package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.BankImport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BankImportJpaRepository extends JpaRepository<BankImport, UUID> {

	List<BankImport> findByClient_IdOrderByCreatedAtDesc(UUID clientId);
}
