package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.domain.model.Category;

import java.util.UUID;

public record CategoryResponse(
		UUID id,
		UUID firmId,
		UUID clientId,
		UUID parentId,
		String code,
		String name,
		Category.CategoryType categoryType,
		boolean system,
		boolean active
) {

	public CategoryResponse(
			UUID id,
			UUID firmId,
			String code,
			String name,
			Category.CategoryType categoryType,
			boolean active
	) {
		this(id, firmId, null, null, code, name, categoryType, false, active);
	}
}
