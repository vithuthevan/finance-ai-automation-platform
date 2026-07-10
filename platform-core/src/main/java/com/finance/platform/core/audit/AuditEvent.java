package com.finance.platform.core.audit;

import com.finance.platform.core.security.TenantContext;
import com.finance.platform.core.security.TenantContextHolder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record AuditEvent(
		UUID firmId,
		UUID actorUserId,
		String actorRole,
		AuditAction action,
		AuditResourceType resourceType,
		UUID resourceId,
		UUID clientId,
		Map<String, Object> beforeState,
		Map<String, Object> afterState,
		Map<String, Object> metadata,
		AuditOutcome outcome
) {

	public AuditEvent {
		beforeState = beforeState == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(beforeState));
		afterState = afterState == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(afterState));
		metadata = metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
		outcome = outcome == null ? AuditOutcome.SUCCESS : outcome;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Prefills firm, actor, and role from the current tenant context when present.
	 */
	public static Builder fromTenant() {
		Builder builder = builder();
		TenantContext context = TenantContextHolder.get();
		if (context != null) {
			builder.firmId(context.firmId())
					.actorUserId(context.userId())
					.actorRole(context.role() != null ? context.role().name() : null);
		}
		return builder;
	}

	public static final class Builder {
		private UUID firmId;
		private UUID actorUserId;
		private String actorRole;
		private AuditAction action;
		private AuditResourceType resourceType;
		private UUID resourceId;
		private UUID clientId;
		private Map<String, Object> beforeState;
		private Map<String, Object> afterState;
		private Map<String, Object> metadata;
		private AuditOutcome outcome = AuditOutcome.SUCCESS;

		public Builder firmId(UUID firmId) {
			this.firmId = firmId;
			return this;
		}

		public Builder actorUserId(UUID actorUserId) {
			this.actorUserId = actorUserId;
			return this;
		}

		public Builder actorRole(String actorRole) {
			this.actorRole = actorRole;
			return this;
		}

		public Builder action(AuditAction action) {
			this.action = action;
			return this;
		}

		public Builder resourceType(AuditResourceType resourceType) {
			this.resourceType = resourceType;
			return this;
		}

		public Builder resourceId(UUID resourceId) {
			this.resourceId = resourceId;
			return this;
		}

		public Builder clientId(UUID clientId) {
			this.clientId = clientId;
			return this;
		}

		public Builder beforeState(Map<String, Object> beforeState) {
			this.beforeState = beforeState;
			return this;
		}

		public Builder afterState(Map<String, Object> afterState) {
			this.afterState = afterState;
			return this;
		}

		public Builder metadata(Map<String, Object> metadata) {
			this.metadata = metadata;
			return this;
		}

		public Builder outcome(AuditOutcome outcome) {
			this.outcome = outcome;
			return this;
		}

		public AuditEvent build() {
			if (action == null) {
				throw new IllegalStateException("action is required");
			}
			if (resourceType == null) {
				throw new IllegalStateException("resourceType is required");
			}
			return new AuditEvent(
					firmId,
					actorUserId,
					actorRole,
					action,
					resourceType,
					resourceId,
					clientId,
					beforeState,
					afterState,
					metadata,
					outcome
			);
		}
	}
}
