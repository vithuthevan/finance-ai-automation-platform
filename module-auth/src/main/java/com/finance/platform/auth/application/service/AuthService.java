package com.finance.platform.auth.application.service;

import com.finance.platform.auth.application.dto.LoginRequest;
import com.finance.platform.auth.application.dto.LoginResponse;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserJpaRepository userRepository;

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
				.orElseThrow(() -> new BusinessException("Invalid credentials"));
		// TODO: BCrypt verification and JWT issuance
		return new LoginResponse(user.getId(), user.getEmail(), user.getFullName());
	}
}
