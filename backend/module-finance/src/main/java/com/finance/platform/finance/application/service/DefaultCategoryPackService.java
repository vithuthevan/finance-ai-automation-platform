package com.finance.platform.finance.application.service;

import com.finance.platform.finance.domain.model.Category;
import com.finance.platform.finance.infrastructure.persistence.CategoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Seeds a lightweight workflow category pack for new firms (not a statutory chart of accounts).
 */
@Service
@RequiredArgsConstructor
public class DefaultCategoryPackService {

	private record PackRow(String code, String name, Category.CategoryType type) {
	}

	private static final List<PackRow> STARTER_PACK = List.of(
			new PackRow("EXP-RENT", "Rent", Category.CategoryType.EXPENSE),
			new PackRow("EXP-UTIL", "Utilities", Category.CategoryType.EXPENSE),
			new PackRow("EXP-BANK", "Bank Charges", Category.CategoryType.EXPENSE),
			new PackRow("EXP-OFFICE", "Office Expenses", Category.CategoryType.EXPENSE),
			new PackRow("EXP-PROF", "Professional Fees", Category.CategoryType.EXPENSE),
			new PackRow("EXP-TRAVEL", "Travel", Category.CategoryType.EXPENSE),
			new PackRow("EXP-SUPPLY", "Supplies", Category.CategoryType.EXPENSE),
			new PackRow("EXP-OTHER", "Other Expenses", Category.CategoryType.EXPENSE),
			new PackRow("INC-SALES", "Sales / Revenue", Category.CategoryType.INCOME),
			new PackRow("INC-SERVICE", "Service Income", Category.CategoryType.INCOME),
			new PackRow("INC-OTHER", "Other Income", Category.CategoryType.INCOME)
	);

	private final CategoryJpaRepository categoryRepository;

	@Transactional
	public void seedForFirm(UUID firmId) {
		for (PackRow row : STARTER_PACK) {
			if (categoryRepository.existsByFirmIdAndCodeAndClientIsNullAndDeletedAtIsNull(firmId, row.code())) {
				continue;
			}
			Category category = Category.builder()
					.code(row.code())
					.name(row.name())
					.categoryType(row.type())
					.system(false)
					.active(true)
					.build();
			category.setFirmId(firmId);
			categoryRepository.save(category);
		}
	}
}
