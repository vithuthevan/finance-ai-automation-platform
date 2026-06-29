package com.finance.platform.finance.domain.model;

import com.finance.platform.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends BaseEntity {

	@Column(nullable = false)
	private java.util.UUID firmId;

	private java.util.UUID clientId;

	@Column(nullable = false, length = 30)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private CategoryType categoryType;

	public enum CategoryType {
		EXPENSE, INCOME, BOTH
	}
}
