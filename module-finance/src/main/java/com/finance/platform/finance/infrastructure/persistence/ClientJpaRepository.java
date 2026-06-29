package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientJpaRepository extends JpaRepository<Client, UUID> {

	Optional<Client> findByIdAndFirmId(UUID id, UUID firmId);

	List<Client> findByFirmIdAndDeletedAtIsNull(UUID firmId);
}
