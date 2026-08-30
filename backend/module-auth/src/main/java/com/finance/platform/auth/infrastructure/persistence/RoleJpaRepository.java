package com.finance.platform.auth.infrastructure.persistence;

import com.finance.platform.auth.domain.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleJpaRepository extends JpaRepository<Role, Short> {

	Optional<Role> findByCode(Role.RoleCode code);
}
