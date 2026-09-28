package com.finance.platform.core.idempotency;

import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.observability.StructuredLog;
import com.finance.platform.core.security.TenantContext;
import com.finance.platform.core.security.TenantContextHolder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Database-backed idempotency claims. The unique key is (firm_id, user_id, key_hash).
 * Operation identity lives in the request fingerprint, so the same key cannot replay a
 * different command. Concurrent claims lose on the unique index in a separate transaction
 * (a unique violation aborts the PostgreSQL transaction, so the loser must re-read afterwards).
 */
@Slf4j
@Service
public class IdempotencyService {

	private final IdempotencyKeyJpaRepository repository;
	private final TransactionTemplate transactionTemplate;
	private final Duration retention;
	private final Duration processingLease;
	private final Counter firstCounter;
	private final Counter replayCounter;
	private final Counter conflictCounter;
	private final Counter inProgressCounter;

	public IdempotencyService(
			IdempotencyKeyJpaRepository repository,
			TransactionTemplate transactionTemplate,
			MeterRegistry meterRegistry,
			@Value("${app.idempotency.retention-hours:24}") long retentionHours,
			@Value("${app.idempotency.processing-lease-seconds:120}") long processingLeaseSeconds
	) {
		this.repository = repository;
		this.transactionTemplate = transactionTemplate;
		this.retention = Duration.ofHours(Math.max(1, retentionHours));
		this.processingLease = Duration.ofSeconds(Math.max(1, processingLeaseSeconds));
		this.firstCounter = meterRegistry.counter("idempotency.first_request");
		this.replayCounter = meterRegistry.counter("idempotency.replay");
		this.conflictCounter = meterRegistry.counter("idempotency.conflict");
		this.inProgressCounter = meterRegistry.counter("idempotency.concurrent_in_progress");
	}

	public IdempotencyBeginResult begin(String rawKey, String method, String path, String requestHash) {
		String key = IdempotencyFingerprint.normalizeKey(rawKey);
		if (path == null || path.isBlank() || path.length() > 500) {
			throw new BusinessException(ErrorCodes.VALIDATION_FAILED, "Request path cannot be fingerprinted");
		}
		TenantContext tenant = TenantContextHolder.require();
		String keyHash = sha256(key);
		for (int attempt = 0; attempt < 3; attempt++) {
			Snapshot existing = read(tenant, keyHash);
			if (existing == null) {
				UUID claimId = UUID.randomUUID();
				if (insert(newClaim(claimId, tenant, keyHash, method, path, requestHash))) {
					record("first_request", path, tenant, keyHash);
					firstCounter.increment();
					return IdempotencyBeginResult.proceed(claimId);
				}
				continue;
			}
			if (existing.status() == IdempotencyKey.Status.COMPLETED && isExpired(existing.createdAt())) {
				deleteScoped(tenant, keyHash, IdempotencyKey.Status.COMPLETED, expiryCutoff());
				continue;
			}
			if (!sameRequest(existing, method, path, requestHash)) {
				record("conflict", path, tenant, keyHash);
				conflictCounter.increment();
				throw new BusinessException(
						ErrorCodes.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST,
						"Idempotency-Key was reused with a different request");
			}
			if (existing.status() == IdempotencyKey.Status.COMPLETED) {
				record("replay", path, tenant, keyHash);
				replayCounter.increment();
				return IdempotencyBeginResult.replay(
						existing.statusCode() == null ? 200 : existing.statusCode(),
						existing.responseBody());
			}
			if (isStale(existing.createdAt())) {
				deleteScoped(tenant, keyHash, IdempotencyKey.Status.STARTED, leaseCutoff());
				continue;
			}
			record("concurrent_in_progress", path, tenant, keyHash);
			inProgressCounter.increment();
			return IdempotencyBeginResult.inProgress();
		}
		record("concurrent_in_progress", path, tenant, keyHash);
		inProgressCounter.increment();
		throw new BusinessException(ErrorCodes.IDEMPOTENCY_CONFLICT,
				"A request with this Idempotency-Key is already in progress");
	}

	public void complete(UUID claimId, int statusCode, String responseBody) {
		if (claimId == null) {
			return;
		}
		String body = responseBody == null ? "" : responseBody;
		if (body.length() > IdempotencyFingerprint.MAX_STORED_RESPONSE_CHARS) {
			release(claimId);
			log.warn("idempotency response exceeded storage cap claimId={} chars={}", claimId, body.length());
			return;
		}
		transactionTemplate.executeWithoutResult(status -> repository.findById(claimId).ifPresent(row -> {
			if (row.getStatus() != IdempotencyKey.Status.STARTED) {
				return;
			}
			row.setStatus(IdempotencyKey.Status.COMPLETED);
			row.setStatusCode(statusCode);
			row.setResponseBody(body);
			row.setCompletedAt(Instant.now());
			repository.save(row);
		}));
	}

	public void release(UUID claimId) {
		if (claimId == null) {
			return;
		}
		transactionTemplate.executeWithoutResult(status ->
				repository.deleteByIdAndStatus(claimId, IdempotencyKey.Status.STARTED));
	}

	public int purgeExpired() {
		Instant cutoff = expiryCutoff();
		Integer removed = transactionTemplate.execute(status -> repository.deleteCreatedBefore(cutoff));
		int count = removed == null ? 0 : removed;
		if (count > 0) {
			Map<String, Object> fields = StructuredLog.baseFields("ok");
			fields.put("result", "purged");
			fields.put("removed", count);
			StructuredLog.info(log, "idempotency", fields);
		}
		return count;
	}

	public static String sha256(String value) {
		return sha256(value == null ? new byte[0] : value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	public static String sha256(byte[] value) {
		try {
			return java.util.HexFormat.of().formatHex(
					java.security.MessageDigest.getInstance("SHA-256").digest(value == null ? new byte[0] : value));
		} catch (Exception ex) {
			throw new IllegalStateException("SHA-256 unavailable");
		}
	}

	private boolean insert(IdempotencyKey row) {
		try {
			transactionTemplate.executeWithoutResult(status -> repository.saveAndFlush(row));
			return true;
		} catch (RuntimeException ex) {
			if (isUniqueViolation(ex)) {
				return false;
			}
			throw ex;
		}
	}

	private Snapshot read(TenantContext tenant, String keyHash) {
		return transactionTemplate.execute(status -> repository
				.findByFirmIdAndUserIdAndKeyHash(tenant.firmId(), tenant.userId(), keyHash)
				.map(Snapshot::from)
				.orElse(null));
	}

	private void deleteScoped(TenantContext tenant, String keyHash, IdempotencyKey.Status rowStatus, Instant cutoff) {
		transactionTemplate.executeWithoutResult(status -> repository.deleteScopedBefore(
				tenant.firmId(), tenant.userId(), keyHash, rowStatus, cutoff));
	}

	private boolean isExpired(Instant createdAt) {
		return createdAt == null || createdAt.isBefore(expiryCutoff());
	}

	private boolean isStale(Instant createdAt) {
		return createdAt == null || createdAt.isBefore(leaseCutoff());
	}

	private Instant expiryCutoff() {
		return Instant.now().minus(retention);
	}

	private Instant leaseCutoff() {
		return Instant.now().minus(processingLease);
	}

	private static boolean sameRequest(Snapshot row, String method, String path, String requestHash) {
		return row.requestHash().equals(requestHash) && row.path().equals(path) && row.method().equals(method);
	}

	private static IdempotencyKey newClaim(
			UUID id,
			TenantContext tenant,
			String keyHash,
			String method,
			String path,
			String requestHash
	) {
		return IdempotencyKey.builder()
				.id(id)
				.firmId(tenant.firmId())
				.userId(tenant.userId())
				.keyHash(keyHash)
				.method(method)
				.path(path)
				.requestHash(requestHash)
				.status(IdempotencyKey.Status.STARTED)
				.createdAt(Instant.now())
				.build();
	}

	private static boolean isUniqueViolation(Throwable ex) {
		Throwable current = ex;
		while (current != null) {
			if (current instanceof DataIntegrityViolationException) {
				return true;
			}
			String message = current.getMessage();
			if (message != null && (message.contains("uq_idempotency_scope") || message.contains("duplicate key"))) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private static void record(String result, String path, TenantContext tenant, String keyHash) {
		Map<String, Object> fields = new LinkedHashMap<>(StructuredLog.baseFields(result));
		fields.put("result", result);
		fields.put("path", path);
		fields.put("firmId", tenant.firmId().toString());
		fields.put("keyHashPrefix", keyHash.substring(0, 12));
		StructuredLog.info(log, "idempotency", fields);
	}

	private record Snapshot(
			IdempotencyKey.Status status,
			Integer statusCode,
			String responseBody,
			String requestHash,
			String method,
			String path,
			Instant createdAt
	) {
		private static Snapshot from(IdempotencyKey row) {
			return new Snapshot(
					row.getStatus(),
					row.getStatusCode(),
					row.getResponseBody(),
					row.getRequestHash(),
					row.getMethod(),
					row.getPath(),
					row.getCreatedAt());
		}
	}
}
