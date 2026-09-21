package com.finance.platform.finance.domain.model.chase;

import com.finance.platform.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "client_chase_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientChasePolicy extends BaseEntity {

	@Column(name = "firm_id", nullable = false)
	private UUID firmId;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false)
	@Builder.Default
	private boolean active = true;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "cadence_days", nullable = false, columnDefinition = "integer[]")
	@Builder.Default
	private int[] cadenceDays = new int[] {0, 3, 7, 10};
}
