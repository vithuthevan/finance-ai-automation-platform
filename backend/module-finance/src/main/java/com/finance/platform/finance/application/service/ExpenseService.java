package com.finance.platform.finance.application.service;

import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.infrastructure.security.SecurityUser;
import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.dto.PageRequests;
import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
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
import com.finance.platform.finance.domain.model.TransactionStatusRules;
import com.finance.platform.finance.infrastructure.persistence.CategoryJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ExpenseJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

	private final ExpenseJpaRepository expenseRepository;
	private final CategoryJpaRepository categoryRepository;
	private final ClientAccessService clientAccessService;
	private final PeriodCloseService periodCloseService;
	private final AuditLogger auditLogger;

	@Transactional(readOnly = true)
	public PageResponse<ExpenseResponse> list(
			UUID clientId,
			TransactionStatus status,
			UUID categoryId,
			LocalDate from,
			LocalDate to,
			int page,
			int size
	) {
		Client client = clientAccessService.requireLedgerRead(clientId);
		Specification<Expense> spec = ledgerSpec(client, resolveListStatuses(status), categoryId, from, to);
		Page<Expense> results = expenseRepository.findAll(
				spec, PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate")));
		return new PageResponse<>(
				results.map(FinanceMapper::toExpenseResponse).getContent(),
				results.getNumber(),
				results.getSize(),
				results.getTotalElements()
		);
	}

	@Transactional(readOnly = true)
	public ExpenseResponse get(UUID clientId, UUID expenseId) {
		clientAccessService.requireLedgerRead(clientId);
		return FinanceMapper.toExpenseResponse(findExpense(clientId, expenseId));
	}

	@Transactional
	public ExpenseResponse create(UUID clientId, CreateExpenseRequest request) {
		Client client = clientAccessService.requireWriteAccess(clientId);
		periodCloseService.assertPeriodOpen(clientId, request.transactionDate());
		User currentUser = clientAccessService.requireCurrentUserEntity();
		Category category = requireExpenseCategory(request.categoryId(), client);
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
		Client client = clientAccessService.requireWriteAccess(clientId);
		Expense expense = findExpense(clientId, expenseId);
		TransactionStatusRules.assertDraft(expense.getStatus());
		periodCloseService.assertPeriodOpen(clientId, expense.getTransactionDate(), request.transactionDate());
		Map<String, Object> before = expenseSnapshot(expense);

		Category category = requireExpenseCategory(request.categoryId(), client);
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
		clientAccessService.requireWriteAccess(clientId);
		Expense expense = findExpense(clientId, expenseId);
		TransactionStatusRules.assertDraft(expense.getStatus());
		periodCloseService.assertPeriodOpen(clientId, expense.getTransactionDate());
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
		clientAccessService.requireApproveAccess(clientId);
		Expense expense = findExpense(clientId, expenseId);
		TransactionStatusRules.assertCanApprove(expense.getStatus());
		periodCloseService.assertPeriodOpen(clientId, expense.getTransactionDate());
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

	@Transactional
	public ExpenseResponse voidExpense(UUID clientId, UUID expenseId, String reason) {
		clientAccessService.requireApproveAccess(clientId);
		Expense expense = findExpense(clientId, expenseId);
		TransactionStatusRules.assertCanVoid(expense.getStatus());
		periodCloseService.assertPeriodOpen(clientId, expense.getTransactionDate());
		String voidReason = requireVoidReason(reason);
		Map<String, Object> before = expenseSnapshot(expense);
		expense.voidExpense(clientAccessService.requireCurrentUserEntity(), voidReason);
		Expense saved = expenseRepository.save(expense);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.EXPENSE_VOIDED)
				.resourceType(AuditResourceType.EXPENSE)
				.resourceId(saved.getId())
				.clientId(clientId)
				.beforeState(before)
				.afterState(expenseSnapshot(saved))
				.build());
		return FinanceMapper.toExpenseResponse(saved);
	}

	private Expense findExpense(UUID clientId, UUID expenseId) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		Expense expense = expenseRepository.findByIdAndClient_IdAndFirmId(expenseId, clientId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId));
		if (SecurityUtils.requireCurrentUser().getRole() == Role.RoleCode.AUDITOR
				&& !TransactionStatusRules.isFinalized(expense.getStatus())) {
			throw new BusinessException(ErrorCodes.ACCESS_DENIED, "Auditors can only view approved or voided expenses");
		}
		return expense;
	}

	private Category requireExpenseCategory(UUID categoryId, Client client) {
		Category category = categoryRepository.findByIdAndFirmIdAndDeletedAtIsNull(categoryId, client.getFirmId())
				.orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
		if (!category.isActive()) {
			throw new BusinessException(ErrorCodes.CATEGORY_INACTIVE, "Category is not active");
		}
		if (category.getCategoryType() == Category.CategoryType.INCOME) {
			throw new BusinessException(ErrorCodes.INVALID_CATEGORY, "Category is not valid for expenses");
		}
		UUID scopedClientId = category.getClient() != null ? category.getClient().getId() : null;
		if (scopedClientId != null && !scopedClientId.equals(client.getId())) {
			throw new BusinessException(ErrorCodes.INVALID_CATEGORY, "Category does not belong to this client");
		}
		return category;
	}

	private void validateAmounts(BigDecimal amount, BigDecimal taxAmount) {
		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new ValidationException("amount", "Amount must be greater than zero");
		}
		if (taxAmount != null && taxAmount.compareTo(BigDecimal.ZERO) < 0) {
			throw new ValidationException("taxAmount", "Tax amount cannot be negative");
		}
		if (taxAmount != null && taxAmount.compareTo(amount) > 0) {
			throw new ValidationException("taxAmount", "Tax amount cannot exceed expense amount");
		}
	}

	private String requireVoidReason(String reason) {
		String trimmed = reason == null ? "" : reason.trim();
		if (trimmed.isBlank()) {
			throw new ValidationException("reason", "Void reason is required");
		}
		return trimmed;
	}

	private List<TransactionStatus> resolveListStatuses(TransactionStatus requested) {
		SecurityUser user = SecurityUtils.requireCurrentUser();
		if (user.getRole() == Role.RoleCode.AUDITOR) {
			if (requested == TransactionStatus.VOID) {
				return List.of(TransactionStatus.VOID);
			}
			if (requested == TransactionStatus.APPROVED) {
				return List.of(TransactionStatus.APPROVED);
			}
			return List.of(TransactionStatus.APPROVED, TransactionStatus.VOID);
		}
		if (requested == null) {
			return List.of();
		}
		return List.of(requested);
	}

	private Specification<Expense> ledgerSpec(
			Client client,
			List<TransactionStatus> statuses,
			UUID categoryId,
			LocalDate from,
			LocalDate to
	) {
		return (root, query, cb) -> {
			List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("firmId"), client.getFirmId()));
			predicates.add(cb.equal(root.get("client").get("id"), client.getId()));
			if (!statuses.isEmpty()) {
				predicates.add(root.get("status").in(statuses));
			}
			if (categoryId != null) {
				predicates.add(cb.equal(root.get("category").get("id"), categoryId));
			}
			if (from != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), from));
			}
			if (to != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), to));
			}
			return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
		};
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
		state.put("voidReason", expense.getVoidReason());
		return state;
	}
}
