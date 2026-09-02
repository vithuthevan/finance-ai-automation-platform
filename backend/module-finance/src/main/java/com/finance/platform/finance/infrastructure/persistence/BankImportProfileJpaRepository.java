package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.BankImportProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankImportProfileJpaRepository extends JpaRepository<BankImportProfile, UUID> {

	List<BankImportProfile> findByFirmIdAndClient_IdOrderByProfileNameAsc(UUID firmId, UUID clientId);

	Optional<BankImportProfile> findByIdAndFirmId(UUID id, UUID firmId);
}
