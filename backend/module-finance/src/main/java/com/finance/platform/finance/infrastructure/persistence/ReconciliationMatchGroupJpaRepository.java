package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.recon.ReconciliationMatchGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconciliationMatchGroupJpaRepository extends JpaRepository<ReconciliationMatchGroup, UUID> {
}
