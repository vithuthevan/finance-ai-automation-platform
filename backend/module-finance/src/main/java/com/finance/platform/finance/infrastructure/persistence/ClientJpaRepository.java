package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientJpaRepository extends JpaRepository<Client, UUID> {

	Optional<Client> findByIdAndFirmId(UUID id, UUID firmId);

	Optional<Client> findByIdAndFirmIdAndDeletedAtIsNull(UUID id, UUID firmId);

	List<Client> findByFirmIdAndDeletedAtIsNull(UUID firmId);

	List<Client> findByFirmIdAndDeletedAtIsNullOrderByNameAsc(UUID firmId);

	List<Client> findByFirmIdAndDeletedAtIsNullAndIdInOrderByNameAsc(UUID firmId, Collection<UUID> ids);

	Page<Client> findByFirmIdAndDeletedAtIsNull(UUID firmId, Pageable pageable);

	Page<Client> findByFirmIdAndDeletedAtIsNullAndIdIn(UUID firmId, Collection<UUID> ids, Pageable pageable);

	boolean existsByFirmIdAndNameAndDeletedAtIsNull(UUID firmId, String name);

	boolean existsByFirmIdAndNameAndDeletedAtIsNullAndIdNot(UUID firmId, String name, UUID id);

	boolean existsByIdAndFirmIdAndDeletedAtIsNullAndActiveTrue(UUID id, UUID firmId);
}
