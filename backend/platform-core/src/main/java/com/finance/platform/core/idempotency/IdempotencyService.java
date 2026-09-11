package com.finance.platform.core.idempotency;

import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.security.TenantContext;
import com.finance.platform.core.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

	private final IdempotencyKeyJpaRepository repository;

	@Transactional
	public Optional<IdempotencyKey> begin(String rawKey, String method, String path, String requestHash) {
		TenantContext tenant = TenantContextHolder.require();
		String keyHash = sha256(rawKey.trim());
		Optional<IdempotencyKey> existing = repository.findByFirmIdAndUserIdAndKeyHash(
				tenant.firmId(), tenant.userId(), keyHash);
		if (existing.isPresent()) {
			IdempotencyKey row = existing.get();
			if (!row.getRequestHash().equals(requestHash) || !row.getPath().equals(path) || !row.getMethod().equals(method)) {
				throw new BusinessException(ErrorCodes.IDEMPOTENCY_CONFLICT,
						"Idempotency-Key was reused with a different request");
			}
			return Optional.of(row);
		}
		IdempotencyKey started = IdempotencyKey.builder()
				.id(UUID.randomUUID())
				.firmId(tenant.firmId())
				.userId(tenant.userId())
				.keyHash(keyHash)
				.method(method)
				.path(path)
				.requestHash(requestHash)
				.status(IdempotencyKey.Status.STARTED)
				.createdAt(Instant.now())
				.build();
		try {
			repository.saveAndFlush(started);
		} catch (DataIntegrityViolationException ex) {
			Optional<IdempotencyKey> raced = repository.findByFirmIdAndUserIdAndKeyHash(
					tenant.firmId(), tenant.userId(), keyHash);
			if (raced.isEmpty()) {
				throw new BusinessException(ErrorCodes.IDEMPOTENCY_CONFLICT,
						"A request with this Idempotency-Key is already in progress");
			}
			IdempotencyKey row = raced.get();
			if (!row.getRequestHash().equals(requestHash) || !row.getPath().equals(path) || !row.getMethod().equals(method)) {
				throw new BusinessException(ErrorCodes.IDEMPOTENCY_CONFLICT,
						"Idempotency-Key was reused with a different request");
			}
			return Optional.of(row);
		}
		return Optional.empty();
	}

	@Transactional(readOnly = true)
	public Optional<IdempotencyKey> findExisting(String rawKey) {
		TenantContext tenant = TenantContextHolder.require();
		return repository.findByFirmIdAndUserIdAndKeyHash(tenant.firmId(), tenant.userId(), sha256(rawKey.trim()));
	}

	@Transactional
	public void complete(String rawKey, int statusCode, String responseBody) {
		TenantContext tenant = TenantContextHolder.require();
		repository.findByFirmIdAndUserIdAndKeyHash(tenant.firmId(), tenant.userId(), sha256(rawKey.trim()))
				.ifPresent(row -> {
					row.setStatus(IdempotencyKey.Status.COMPLETED);
					row.setStatusCode(statusCode);
					row.setResponseBody(responseBody);
					row.setCompletedAt(Instant.now());
					repository.save(row);
				});
	}

	public static String sha256(String value) {
		return sha256(value.getBytes(StandardCharsets.UTF_8));
	}

	public static String sha256(byte[] value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value == null ? new byte[0] : value));
		} catch (Exception ex) {
			throw new IllegalStateException("SHA-256 unavailable");
		}
	}
}
