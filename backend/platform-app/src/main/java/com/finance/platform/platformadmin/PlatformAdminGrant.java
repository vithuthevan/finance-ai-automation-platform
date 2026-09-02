package com.finance.platform.platformadmin;

import com.finance.platform.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_admin_grants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformAdminGrant extends BaseEntity {

	@Column(nullable = false, unique = true)
	private UUID userId;

	@Column(nullable = false)
	@Builder.Default
	private boolean active = true;

	@Column(nullable = false)
	private Instant grantedAt;

	private UUID grantedBy;

	private Instant revokedAt;

	private UUID revokedBy;

	private String reason;
}
