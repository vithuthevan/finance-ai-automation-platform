package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryJpaRepository extends JpaRepository<Category, UUID> {

	Optional<Category> findByIdAndFirmId(UUID id, UUID firmId);

	Optional<Category> findByIdAndFirmIdAndDeletedAtIsNull(UUID id, UUID firmId);

	List<Category> findAllByFirmIdAndDeletedAtIsNull(UUID firmId);

	boolean existsByFirmIdAndCodeAndDeletedAtIsNull(UUID firmId, String code);

	boolean existsByFirmIdAndCodeAndClientIsNullAndDeletedAtIsNull(UUID firmId, String code);

	boolean existsByFirmIdAndCodeAndClientIsNullAndDeletedAtIsNullAndIdNot(UUID firmId, String code, UUID id);

	boolean existsByFirmIdAndClient_IdAndCodeAndDeletedAtIsNull(UUID firmId, UUID clientId, String code);

	boolean existsByFirmIdAndClient_IdAndCodeAndDeletedAtIsNullAndIdNot(
			UUID firmId, UUID clientId, String code, UUID id);
}
