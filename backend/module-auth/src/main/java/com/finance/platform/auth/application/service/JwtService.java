package com.finance.platform.auth.application.service;

import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.infrastructure.security.JwtProperties;
import com.finance.platform.auth.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

	private final JwtTokenProvider jwtTokenProvider;
	private final JwtProperties jwtProperties;

	public IssuedAccessToken issueAccessToken(User user) {
		String accessToken = jwtTokenProvider.generateAccessToken(user);
		return new IssuedAccessToken(accessToken, jwtProperties.expirationMs());
	}

	public record IssuedAccessToken(String accessToken, long expiresInMs) {
	}
}
