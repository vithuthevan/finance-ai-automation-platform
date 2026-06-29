package com.finance.platform.auth.infrastructure.security;

import com.finance.platform.auth.domain.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

	private final JwtProperties jwtProperties;
	private final SecretKey secretKey;

	public JwtTokenProvider(JwtProperties jwtProperties) {
		this.jwtProperties = jwtProperties;
		this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(toBase64Secret(jwtProperties.secret())));
	}

	public String generateToken(UUID userId, UUID firmId, Role.RoleCode role, String email) {
		Instant now = Instant.now();
		Instant expiry = now.plusMillis(jwtProperties.expirationMs());

		return Jwts.builder()
				.subject(userId.toString())
				.claim("firmId", firmId.toString())
				.claim("role", role.name())
				.claim("email", email)
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiry))
				.signWith(secretKey)
				.compact();
	}

	public JwtClaims parseToken(String token) {
		Claims claims = Jwts.parser()
				.verifyWith(secretKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();

		return new JwtClaims(
				UUID.fromString(claims.getSubject()),
				UUID.fromString(claims.get("firmId", String.class)),
				Role.RoleCode.valueOf(claims.get("role", String.class)),
				claims.get("email", String.class),
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

	private String toBase64Secret(String secret) {
		if (secret.matches("^[A-Za-z0-9+/=]+$") && secret.length() >= 44) {
			return secret;
		}
		return java.util.Base64.getEncoder().encodeToString(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	public record JwtClaims(UUID userId, UUID firmId, Role.RoleCode role, String email, Instant expiresAt) {
	}
}
