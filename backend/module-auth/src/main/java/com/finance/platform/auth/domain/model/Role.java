package com.finance.platform.auth.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

	@Id
	private Short id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, unique = true, length = 30)
	private RoleCode code;

	@Column(nullable = false, length = 100)
	private String name;

	public enum RoleCode {
		ADMIN, ACCOUNTANT, AUDITOR, BUSINESS_OWNER
	}
}
