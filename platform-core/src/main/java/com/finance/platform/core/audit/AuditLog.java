package com.finance.platform.core.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit row. Do not add update/delete repository methods.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "firm_id", updatable = false)
	private UUID firmId;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(name = "actor_user_id", updatable = false)
	private UUID actorUserId;

	@Column(name = "actor_role", length = 40, updatable = false)
	private String actorRole;

	@Column(nullable = false, length = 80, updatable = false)
	private String action;

	@Column(name = "resource_type", nullable = false, length = 40, updatable = false)
	private String resourceType;

	@Column(name = "resource_id", updatable = false)
	private UUID resourceId;

	@Column(name = "client_id", updatable = false)
	private UUID clientId;

	@Column(name = "correlation_id", length = 100, updatable = false)
	private String correlationId;

	@Column(name = "ip_address", length = 45, updatable = false)
	private String ipAddress;

	@Column(name = "user_agent", length = 512, updatable = false)
	private String userAgent;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "before_state", columnDefinition = "jsonb", updatable = false)
	private Map<String, Object> beforeState;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "after_state", columnDefinition = "jsonb", updatable = false)
	private Map<String, Object> afterState;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb", updatable = false)
	private Map<String, Object> metadata;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private AuditOutcome outcome;
}
