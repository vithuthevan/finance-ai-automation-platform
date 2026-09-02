package com.finance.platform.auth.infrastructure.persistence;

import com.finance.platform.auth.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmailAndDeletedAtIsNull(String email);

	List<User> findByFirmIdAndDeletedAtIsNull(UUID firmId);

	Page<User> findByFirmIdAndDeletedAtIsNull(UUID firmId, Pageable pageable);

	Optional<User> findByIdAndFirmIdAndDeletedAtIsNull(UUID id, UUID firmId);

	@Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.id = :id")
	Optional<User> findDetailedById(@Param("id") UUID id);
}
