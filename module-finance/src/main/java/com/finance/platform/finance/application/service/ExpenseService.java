package com.finance.platform.finance.application.service;

import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.infrastructure.security.SecurityUser;
import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.finance.application.dto.CreateExpenseRequest;
import com.finance.platform.finance.application.dto.UpdateExpenseRequest;
import com.finance.platform.finance.application.dto.ExpenseResponse;
import com.finance.platform.finance.application.mapper.FinanceMapper;
import com.finance.platform.finance.domain.model.Category;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.domain.model.Expense;
import com.finance.platform.finance.domain.model.TransactionSource;
import com.finance.platform.finance.domain.model.TransactionStatus;
import com.finance.platform.finance.infrastructure.persistence.CategoryJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ExpenseJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

	private final ExpenseJpaRepository expenseRepository;
	private final CategoryJpaRepository categoryRepository;
	private final ClientAccessService clientAccessService;
	private final AuditLogger auditLogger;

	@Transactional(readOnly = true)
	public PageResponse<ExpenseResponse> list(UUID clientId, TransactionStatus status, int page, int size) {
		clientAccessService.requireAccessibleClient(clientId);
		TransactionStatus effectiveStatus = resolveListStatus(status);

		PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate"));
		Page<Expense> results = effectiveStatus == null
				? expenseRepository.findByClientId(clientId, pageable)
				: expenseRepository.findByClientIdAndStatus(clientId, effectiveStatus, pageable);

		return new PageResponse<>(
				results.map(FinanceMapper::toExpenseResponse).getContent(),
				results.getNumber(),
				results.getSize(),
				results.getTotalElements()
		);
	}

	@Transactional(readOnly = true)
	public ExpenseResponse get(UUID clientId, UUID expenseId) {
		clientAccessService.requireAccessibleClient(clientId);
		return FinanceMapper.toExpenseResponse(findExpense(clientId, expenseId));
	}

	@Transactional
	public ExpenseResponse create(UUID clientId, CreateExpenseRequest request) {
		Client client = clientAccessService.requireAccessibleClient(clientId);
		User currentUser = clientAccessService.requireCurrentUserEntity();
		Category category = requireExpenseCategory(request.categoryId(), client.getFirmId());
		validateAmounts(request.amount(), request.taxAmount());

		Expense expense = Expense.builder()
				.client(client)
				.category(category)
				.transactionDate(request.transactionDate())
				.amount(request.amount())
				.currencyCode(request.currencyCode() != null ? request.currencyCode() : "LKR")
				.vendorName(request.vendorName())
				.description(request.description())
				.taxAmount(request.taxAmount())
				.referenceNo(request.referenceNo())
				.status(TransactionStatus.DRAFT)
				.source(TransactionSource.MANUAL)
				.createdByUser(currentUser)
				.build();
		expense.setFirmId(client.getFirmId());

		Expense saved = expenseRepository.save(expense);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.EXPENSE_CREATED)
				.resourceType(AuditResourceType.EXPENSE)
				.resourceId(saved.getId())
				.clientId(clientId)
				.afterState(expenseSnapshot(saved))
				.build());
		return FinanceMapper.toExpenseResponse(saved);
	}

	@Transactional
	public ExpenseResponse update(UUID clientId, UUID expenseId, UpdateExpenseRequest request) {
		Client client = clientAccessService.requireAccessibleClient(clientId);
		Expense expense = findExpense(clientId, expenseId);
		ensureDraft(expense);
		Map<String, Object> before = expenseSnapshot(expense);

		Category category = requireExpenseCategory(request.categoryId(), client.getFirmId());
		validateAmounts(request.amount(), request.taxAmount());
		expense.setCategory(category);
		expense.setTransactionDate(request.transactionDate());
		expense.setAmount(request.amount());
		expense.setCurrencyCode(request.currencyCode() != null ? request.currencyCode() : "LKR");
		expense.setVendorName(request.vendorName());
		expense.setDescription(request.description());
		expense.setTaxAmount(request.taxAmount());
		expense.setReferenceNo(request.referenceNo());

		Expense saved = expenseRepository.save(expense);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.EXPENSE_UPDATED)
				.resourceType(AuditResourceType.EXPENSE)
				.resourceId(saved.getId())
				.clientId(clientId)
				.beforeState(before)
				.afterState(expenseSnapshot(saved))
				.build());
		return FinanceMapper.toExpenseResponse(saved);
	}

	@Transactional
	public void delete(UUID clientId, UUID expenseId) {
		clientAccessService.requireAccessibleClient(clientId);
		Expense expense = findExpense(clientId, expenseId);
		ensureDraft(expense);
		Map<String, Object> before = expenseSnapshot(expense);
		UUID firmId = expense.getFirmId();
		UUID id = expense.getId();
		expenseRepository.delete(expense);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(firmId)
				.action(AuditAction.EXPENSE_DELETED)
				.resourceType(AuditResourceType.EXPENSE)
				.resourceId(id)
				.clientId(clientId)
				.beforeState(before)
				.build());
	}

	@Transactional
	public ExpenseResponse approve(UUID clientId, UUID expenseId) {
		clientAccessService.requireAccessibleClient(clientId);
		assertCanApprove();
		Expense expense = findExpense(clientId, expenseId);
		Map<String, Object> before = expenseSnapshot(expense);
		expense.approve(clientAccessService.requireCurrentUserEntity());
		Expense saved = expenseRepository.save(expense);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.EXPENSE_APPROVED)
				.resourceType(AuditResourceType.EXPENSE)
				.resourceId(saved.getId())
				.clientId(clientId)
				.beforeState(before)
				.afterState(expenseSnapshot(saved))
				.build());
		return FinanceMapper.toExpenseResponse(saved);
	}

	private Expense findExpense(UUID clientId, UUID expenseId) {
		Expense expense = expenseRepository.findByIdAndClientId(expenseId, clientId)
				.orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId));
		if (SecurityUtils.requireCurrentUser().getRole() == Role.RoleCode.AUDITOR
				&& expense.getStatus() != TransactionStatus.APPROVED) {
			throw new BusinessException("Auditors can only view approved expenses");
		}
		return expense;
	}

	private Category requireExpenseCategory(UUID categoryId, UUID firmId) {
		Category category = categoryRepository.findByIdAndFirmId(categoryId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
		if (category.getDeletedAt() != null) {
			throw new ResourceNotFoundException("Category", categoryId);
		}
		if (!category.isActive()) {
			throw new ValidationException("categoryId", "Category is not active");
		}
		if (category.getCategoryType() == Category.CategoryType.INCOME) {
			throw new ValidationException("categoryId", "Category is not valid for expenses");
		}
		return category;
	}

	private void validateAmounts(BigDecimal amount, BigDecimal taxAmount) {
		if (taxAmount != null && taxAmount.compareTo(amount) > 0) {
			throw new ValidationException("taxAmount", "Tax amount cannot exceed expense amount");
		}
	}

	private void ensureDraft(Expense expense) {
		if (expense.getStatus() != TransactionStatus.DRAFT) {
			throw new BusinessException("Only draft expenses can be modified or deleted");
		}
	}

	private void assertCanApprove() {
		Role.RoleCode role = SecurityUtils.requireCurrentUser().getRole();
		if (role != Role.RoleCode.ADMIN && role != Role.RoleCode.ACCOUNTANT) {
			throw new BusinessException("Only administrators and accountants can approve expenses");
		}
	}

	private TransactionStatus resolveListStatus(TransactionStatus requested) {
		SecurityUser user = SecurityUtils.requireCurrentUser();
		if (user.getRole() == Role.RoleCode.AUDITOR) {
			return TransactionStatus.APPROVED;
		}
		if (user.getRole() == Role.RoleCode.BUSINESS_OWNER && requested == null) {
			return TransactionStatus.APPROVED;
		}
		return requested;
	}

	private static Map<String, Object> expenseSnapshot(Expense expense) {
		Map<String, Object> state = new LinkedHashMap<>();
		state.put("amount", expense.getAmount());
		state.put("currencyCode", expense.getCurrencyCode());
		state.put("categoryId", expense.getCategory() != null ? expense.getCategory().getId() : null);
		state.put("transactionDate", expense.getTransactionDate() != null
				? expense.getTransactionDate().toString() : null);
		state.put("vendorName", expense.getVendorName());
		state.put("description", expense.getDescription());
		state.put("taxAmount", expense.getTaxAmount());
		state.put("referenceNo", expense.getReferenceNo());
		state.put("status", expense.getStatus() != null ? expense.getStatus().name() : null);
		return state;
	}
}
