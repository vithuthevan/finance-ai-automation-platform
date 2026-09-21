package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.recon.ReconciliationMatchGroupItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconciliationMatchGroupItemJpaRepository extends JpaRepository<ReconciliationMatchGroupItem, UUID> {
}
