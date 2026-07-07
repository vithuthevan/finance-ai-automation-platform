package com.finance.platform.auth.application.service;

import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.infrastructure.persistence.RoleJpaRepository;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserRegistrationService {

	private final UserJpaRepository userRepository;
	private final RoleJpaRepository roleRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public User registerAdmin(UUID firmId, String email, String rawPassword, String fullName) {
		if (userRepository.findByEmailAndDeletedAtIsNull(email).isPresent()) {
			throw new BusinessException("Email already registered");
		}

		Role adminRole = roleRepository.findByCode(Role.RoleCode.ADMIN)
				.orElseThrow(() -> new BusinessException("Admin role not configured"));

		User user = User.builder()
				.role(adminRole)
				.email(email)
				.passwordHash(passwordEncoder.encode(rawPassword))
				.fullName(fullName)
				.active(true)
				.build();
		user.setFirmId(firmId);

		return userRepository.save(user);
	}
}
