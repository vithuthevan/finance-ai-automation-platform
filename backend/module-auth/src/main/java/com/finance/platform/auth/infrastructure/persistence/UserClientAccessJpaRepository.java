package com.finance.platform.auth.infrastructure.persistence;

import com.finance.platform.auth.domain.model.UserClientAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserClientAccessJpaRepository extends JpaRepository<UserClientAccess, UUID> {

	List<UserClientAccess> findByUser_Id(UUID userId);

	List<UserClientAccess> findByUser_IdIn(Collection<UUID> userIds);

	Optional<UserClientAccess> findByUser_IdAndClientId(UUID userId, UUID clientId);

	void deleteByUser_Id(UUID userId);

	@Query("""
			select uca from UserClientAccess uca
			join fetch uca.user u
			where uca.clientId = :clientId and u.deletedAt is null
			""")
	List<UserClientAccess> findByClientId(@Param("clientId") UUID clientId);
}
