package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.invoicing.ArCustomer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArCustomerJpaRepository extends JpaRepository<ArCustomer, UUID> {

	List<ArCustomer> findByFirmIdAndActiveTrueOrderByNameAsc(UUID firmId);

	List<ArCustomer> findByFirmIdOrderByNameAsc(UUID firmId);

	Optional<ArCustomer> findByIdAndFirmId(UUID id, UUID firmId);
}
