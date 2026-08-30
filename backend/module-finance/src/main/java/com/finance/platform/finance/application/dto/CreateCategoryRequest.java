package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.domain.model.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCategoryRequest(
		@NotBlank(message = "Category code is required")
		@Size(max = 30, message = "Category code must not exceed 30 characters")
		String code,
		@NotBlank(message = "Category name is required")
		@Size(max = 100, message = "Category name must not exceed 100 characters")
		String name,
		@NotNull(message = "Category type is required")
		Category.CategoryType categoryType,
		UUID clientId,
		UUID parentId
) {

	public CreateCategoryRequest(String code, String name, Category.CategoryType categoryType) {
		this(code, name, categoryType, null, null);
	}
}
