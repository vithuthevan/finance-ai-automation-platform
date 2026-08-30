package com.finance.platform.finance.application.service;

import com.finance.platform.finance.domain.model.Category;
import com.finance.platform.finance.domain.model.Expense;
import com.finance.platform.finance.domain.model.Income;
import com.finance.platform.finance.domain.model.TransactionStatus;
import com.finance.platform.finance.infrastructure.persistence.CategoryJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ExpenseJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.IncomeJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategorySuggestionService {

	private final ExpenseJpaRepository expenseRepository;
	private final IncomeJpaRepository incomeRepository;
	private final CategoryJpaRepository categoryRepository;

	@Transactional(readOnly = true)
	public Optional<Category> suggestForVendor(UUID firmId, UUID clientId, String vendorName) {
		if (vendorName == null || vendorName.isBlank()) {
			return Optional.empty();
		}
		return expenseRepository
				.findFirstByFirmIdAndClient_IdAndStatusAndVendorNameIgnoreCaseOrderByApprovedAtDesc(
						firmId, clientId, TransactionStatus.APPROVED, vendorName.trim())
				.map(Expense::getCategory)
				.or(() -> findByCode(firmId, clientId, vendorName));
	}

	@Transactional(readOnly = true)
	public Optional<Category> suggestForCustomer(UUID firmId, UUID clientId, String customerName) {
		if (customerName == null || customerName.isBlank()) {
			return Optional.empty();
		}
		return incomeRepository
				.findFirstByFirmIdAndClient_IdAndStatusAndCustomerNameIgnoreCaseOrderByApprovedAtDesc(
						firmId, clientId, TransactionStatus.APPROVED, customerName.trim())
				.map(Income::getCategory)
				.or(() -> findByCode(firmId, clientId, customerName));
	}

	@Transactional(readOnly = true)
	public Optional<Category> findByCode(UUID firmId, UUID clientId, String codeOrName) {
		if (codeOrName == null || codeOrName.isBlank()) {
			return Optional.empty();
		}
		String needle = codeOrName.trim();
		return categoryRepository.findAllByFirmIdAndDeletedAtIsNull(firmId).stream()
				.filter(Category::isActive)
				.filter(category -> category.getClient() == null
						|| (clientId != null && clientId.equals(category.getClient().getId())))
				.filter(category -> needle.equalsIgnoreCase(category.getCode())
						|| needle.equalsIgnoreCase(category.getName()))
				.findFirst();
	}
}
