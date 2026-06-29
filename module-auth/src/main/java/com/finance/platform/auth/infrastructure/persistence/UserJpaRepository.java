package com.finance.platform.auth.infrastructure.persistence;

import com.finance.platform.auth.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmailAndDeletedAtIsNull(String email);

	List<User> findByFirmIdAndDeletedAtIsNull(UUID firmId);

	Optional<User> findByIdAndFirmIdAndDeletedAtIsNull(UUID id, UUID firmId);
}
