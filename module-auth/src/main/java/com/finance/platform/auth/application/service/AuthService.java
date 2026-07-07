package com.finance.platform.auth.application.service;

import com.finance.platform.auth.application.dto.LoginRequest;
import com.finance.platform.auth.application.dto.LoginResponse;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.auth.infrastructure.security.JwtProperties;
import com.finance.platform.auth.infrastructure.security.JwtTokenProvider;
import com.finance.platform.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final AuthenticationManager authenticationManager;
	private final UserJpaRepository userRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final JwtProperties jwtProperties;

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(request.email(), request.password())
		);

		User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
				.orElseThrow(() -> new BusinessException("Invalid credentials"));

		return buildAuthResponse(user);
	}

	public LoginResponse buildAuthResponse(User user) {
		String token = jwtTokenProvider.generateToken(
				user.getId(),
				user.getFirmId(),
				user.getRole().getCode(),
				user.getEmail()
		);

		return new LoginResponse(
				token,
				"Bearer",
				jwtProperties.expirationMs(),
				user.getId(),
				user.getEmail(),
				user.getFullName(),
				user.getRole().getCode().name()
		);
	}
}
