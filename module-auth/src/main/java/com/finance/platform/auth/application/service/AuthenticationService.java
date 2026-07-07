package com.finance.platform.auth.application.service;

import com.finance.platform.auth.application.dto.LoginRequest;
import com.finance.platform.auth.application.dto.LoginResponse;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

	private final AuthenticationManager authenticationManager;
	private final UserJpaRepository userRepository;
	private final JwtService jwtService;

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		try {
			authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(request.email(), request.password())
			);
		} catch (BadCredentialsException ex) {
			throw new BusinessException("Invalid credentials");
		}

		User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
				.orElseThrow(() -> new BusinessException("Invalid credentials"));

		return buildLoginResponse(user);
	}

	private LoginResponse buildLoginResponse(User user) {
		JwtService.IssuedAccessToken issuedToken = jwtService.issueAccessToken(user);

		return new LoginResponse(
				issuedToken.accessToken(),
				"Bearer",
				issuedToken.expiresInMs(),
				user.getId(),
				user.getEmail(),
				user.getFullName(),
				user.getRole().getCode().name()
		);
	}
}
