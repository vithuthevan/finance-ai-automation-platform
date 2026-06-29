package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CategoryJpaRepository extends JpaRepository<Category, UUID> {

	Optional<Category> findByIdAndFirmId(UUID id, UUID firmId);
}
