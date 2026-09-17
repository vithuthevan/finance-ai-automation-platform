package com.finance.platform.core.audit;

import com.finance.platform.core.observability.ObservabilityMdc;
import com.finance.platform.core.observability.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JpaAuditLogger implements AuditLogger {

	private static final int MAX_USER_AGENT_LENGTH = 512;

	private final AuditLogJpaRepository auditLogRepository;

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void record(AuditEvent event) {
		persist(event);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordIndependent(AuditEvent event) {
		persist(event);
	}

	private void persist(AuditEvent event) {
		AuditLog log = new AuditLog();
		log.setFirmId(event.firmId());
		log.setOccurredAt(Instant.now());
		log.setActorUserId(event.actorUserId());
		log.setActorRole(event.actorRole());
		log.setAction(event.action().name());
		log.setResourceType(event.resourceType().name());
		log.setResourceId(event.resourceId());
		log.setClientId(event.clientId());
		log.setBeforeState(event.beforeState());
		log.setAfterState(event.afterState());
		log.setMetadata(event.metadata());
		log.setOutcome(event.outcome());
		enrichFromHttpRequest(log);
		auditLogRepository.save(log);
	}

	private void enrichFromHttpRequest(AuditLog log) {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
			if (log.getCorrelationId() == null) {
				log.setCorrelationId(UUID.randomUUID().toString());
			}
			return;
		}

		HttpServletRequest request = servletAttributes.getRequest();
		String correlationId = ObservabilityMdc.currentRequestId();
		if ("unknown".equals(correlationId)) {
			String header = request.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
			correlationId = header != null && !header.isBlank() ? header.trim() : UUID.randomUUID().toString();
		}
		log.setCorrelationId(correlationId);
		log.setIpAddress(resolveClientIp(request));
		log.setUserAgent(truncate(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH));
	}

	private static String resolveClientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			int comma = forwarded.indexOf(',');
			return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
		}
		return request.getRemoteAddr();
	}

	private static String truncate(String value, int maxLength) {
		if (value == null) {
			return null;
		}
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}
}
