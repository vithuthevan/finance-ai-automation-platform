package com.finance.platform.controller.finance;

import com.finance.platform.finance.application.dto.CategoryResponse;
import com.finance.platform.finance.application.dto.CreateCategoryRequest;
import com.finance.platform.finance.application.dto.UpdateCategoryRequest;
import com.finance.platform.finance.application.service.CategoryService;
import com.finance.platform.finance.domain.model.Category;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

	private final CategoryService categoryService;

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public List<CategoryResponse> listCategories(
			@RequestParam(required = false) UUID clientId,
			@RequestParam(required = false) Category.CategoryType categoryType,
			@RequestParam(required = false) Boolean active
	) {
		return categoryService.listFirmCategories(clientId, categoryType, active);
	}

	@GetMapping("/{categoryId}")
	@PreAuthorize("isAuthenticated()")
	public CategoryResponse getCategory(@PathVariable UUID categoryId) {
		return categoryService.get(categoryId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMIN')")
	public CategoryResponse createCategory(@Valid @RequestBody CreateCategoryRequest request) {
		return categoryService.create(request);
	}

	@PutMapping("/{categoryId}")
	@PreAuthorize("hasRole('ADMIN')")
	public CategoryResponse updateCategory(
			@PathVariable UUID categoryId,
			@Valid @RequestBody UpdateCategoryRequest request
	) {
		return categoryService.update(categoryId, request);
	}

	@PostMapping("/{categoryId}/activate")
	@PreAuthorize("hasRole('ADMIN')")
	public CategoryResponse activateCategory(@PathVariable UUID categoryId) {
		return categoryService.setActive(categoryId, true);
	}

	@PostMapping("/{categoryId}/deactivate")
	@PreAuthorize("hasRole('ADMIN')")
	public CategoryResponse deactivateCategory(@PathVariable UUID categoryId) {
		return categoryService.setActive(categoryId, false);
	}
}
