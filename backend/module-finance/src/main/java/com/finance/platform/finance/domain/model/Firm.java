package com.finance.platform.finance.domain.model;

import com.finance.platform.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "firms", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Firm extends BaseEntity {

	@Column(nullable = false, length = 200)
	private String name;

	@Column(length = 50)
	private String registrationNo;

	@Column(nullable = false, length = 3)
	@Builder.Default
	private String currencyCode = "LKR";

	@Column(nullable = false, length = 80)
	@Builder.Default
	private String timezone = "Asia/Colombo";

	@Column(nullable = false)
	@Builder.Default
	private int financialYearStartMonth = 4;

	@Column(nullable = false)
	@Builder.Default
	private boolean aiEnabled = true;

	@Column(nullable = false)
	@Builder.Default
	private boolean active = true;
}
