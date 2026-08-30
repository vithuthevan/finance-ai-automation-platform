package com.finance.platform.auth.infrastructure.persistence;

import com.finance.platform.auth.domain.model.UserClientAccess;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserClientAccessJpaRepository extends JpaRepository<UserClientAccess, UUID> {

	List<UserClientAccess> findByUser_Id(UUID userId);

	List<UserClientAccess> findByUser_IdIn(Collection<UUID> userIds);

	Optional<UserClientAccess> findByUser_IdAndClientId(UUID userId, UUID clientId);

	void deleteByUser_Id(UUID userId);
}
