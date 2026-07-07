package com.finance.platform.auth.infrastructure.security;

import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

	private final JwtProperties jwtProperties;
	private final SecretKey secretKey;

	public JwtTokenProvider(JwtProperties jwtProperties) {
		this.jwtProperties = jwtProperties;
		this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(resolveBase64Secret(jwtProperties.secret())));
	}

	public String generateAccessToken(User user) {
		Instant now = Instant.now();
		Instant expiry = now.plusMillis(jwtProperties.expirationMs());

		return Jwts.builder()
				.id(UUID.randomUUID().toString())
				.subject(user.getId().toString())
				.issuer(jwtProperties.issuer())
				.claim(JwtClaimNames.FIRM_ID, user.getFirmId().toString())
				.claim(JwtClaimNames.ROLE, user.getRole().getCode().name())
				.claim(JwtClaimNames.EMAIL, user.getEmail())
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiry))
				.signWith(secretKey)
				.compact();
	}

	public JwtClaims parseToken(String token) {
		Claims claims = Jwts.parser()
				.requireIssuer(jwtProperties.issuer())
				.verifyWith(secretKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();

		return new JwtClaims(
				UUID.fromString(claims.getSubject()),
				UUID.fromString(claims.get(JwtClaimNames.FIRM_ID, String.class)),
				Role.RoleCode.valueOf(claims.get(JwtClaimNames.ROLE, String.class)),
				claims.get(JwtClaimNames.EMAIL, String.class),
				claims.getId(),
				claims.getExpiration().toInstant()
		);
	}

	public boolean isValid(String token) {
		try {
			parseToken(token);
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

	private String resolveBase64Secret(String secret) {
		if (secret.matches("^[A-Za-z0-9+/=]+$") && secret.length() >= 44) {
			return secret;
		}
		return Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8));
	}

	public record JwtClaims(
			UUID userId,
			UUID firmId,
			Role.RoleCode role,
			String email,
			String tokenId,
			Instant expiresAt
	) {
	}
}
