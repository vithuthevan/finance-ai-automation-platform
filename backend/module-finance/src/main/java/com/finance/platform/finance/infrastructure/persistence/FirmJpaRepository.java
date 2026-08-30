package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.Firm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FirmJpaRepository extends JpaRepository<Firm, UUID> {

	boolean existsByName(String name);
}
