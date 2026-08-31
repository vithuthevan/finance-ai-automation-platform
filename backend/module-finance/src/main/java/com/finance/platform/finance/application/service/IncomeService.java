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
import com.finance.platform.finance.application.dto.IncomeRequest;
import com.finance.platform.finance.application.dto.IncomeResponse;
import com.finance.platform.finance.application.mapper.FinanceMapper;
import com.finance.platform.finance.domain.model.Category;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.domain.model.Income;
import com.finance.platform.finance.domain.model.TransactionSource;
import com.finance.platform.finance.domain.model.TransactionStatus;
import com.finance.platform.finance.domain.model.TransactionStatusRules;
import com.finance.platform.finance.infrastructure.persistence.CategoryJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.IncomeJpaRepository;
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
public class IncomeService {

	private final IncomeJpaRepository incomeRepository;
	private final CategoryJpaRepository categoryRepository;
	private final ClientAccessService clientAccessService;
	private final PeriodCloseService periodCloseService;
	private final AuditLogger auditLogger;

	@Transactional(readOnly = true)
	public PageResponse<IncomeResponse> list(
			UUID clientId,
			TransactionStatus status,
			UUID categoryId,
			LocalDate from,
			LocalDate to,
			int page,
			int size
	) {
		Client client = clientAccessService.requireLedgerRead(clientId);
		Specification<Income> spec = ledgerSpec(client, resolveListStatuses(status), categoryId, from, to);
		Page<Income> results = incomeRepository.findAll(
				spec, PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate")));
		return new PageResponse<>(
				results.map(FinanceMapper::toIncomeResponse).getContent(),
				results.getNumber(),
				results.getSize(),
				results.getTotalElements()
		);
	}

	@Transactional(readOnly = true)
	public IncomeResponse get(UUID clientId, UUID incomeId) {
		clientAccessService.requireLedgerRead(clientId);
		return FinanceMapper.toIncomeResponse(findIncome(clientId, incomeId));
	}

	@Transactional
	public IncomeResponse create(UUID clientId, IncomeRequest request) {
		Client client = clientAccessService.requireWriteAccess(clientId);
		periodCloseService.assertPeriodOpen(clientId, request.transactionDate());
		User currentUser = clientAccessService.requireCurrentUserEntity();
		Category category = requireIncomeCategory(request.categoryId(), client);
		validateAmounts(request.amount(), request.taxAmount());

		Income income = Income.builder()
				.client(client)
				.category(category)
				.transactionDate(request.transactionDate())
				.amount(request.amount())
				.currencyCode(request.currencyCode() != null ? request.currencyCode() : "LKR")
				.customerName(request.customerName())
				.description(request.description())
				.paymentMethod(request.paymentMethod())
				.taxAmount(request.taxAmount())
				.referenceNo(request.referenceNo())
				.status(TransactionStatus.DRAFT)
				.source(TransactionSource.MANUAL)
				.createdByUser(currentUser)
				.build();
		income.setFirmId(client.getFirmId());

		Income saved = incomeRepository.save(income);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.INCOME_CREATED)
				.resourceType(AuditResourceType.INCOME)
				.resourceId(saved.getId())
				.clientId(clientId)
				.afterState(incomeSnapshot(saved))
				.build());
		return FinanceMapper.toIncomeResponse(saved);
	}

	@Transactional
	public IncomeResponse update(UUID clientId, UUID incomeId, IncomeRequest request) {
		Client client = clientAccessService.requireWriteAccess(clientId);
		Income income = findIncome(clientId, incomeId);
		TransactionStatusRules.assertDraft(income.getStatus());
		periodCloseService.assertPeriodOpen(clientId, income.getTransactionDate(), request.transactionDate());
		Map<String, Object> before = incomeSnapshot(income);

		Category category = requireIncomeCategory(request.categoryId(), client);
		validateAmounts(request.amount(), request.taxAmount());
		income.setCategory(category);
		income.setTransactionDate(request.transactionDate());
		income.setAmount(request.amount());
		income.setCurrencyCode(request.currencyCode() != null ? request.currencyCode() : "LKR");
		income.setCustomerName(request.customerName());
		income.setDescription(request.description());
		income.setPaymentMethod(request.paymentMethod());
		income.setTaxAmount(request.taxAmount());
		income.setReferenceNo(request.referenceNo());

		Income saved = incomeRepository.save(income);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.INCOME_UPDATED)
				.resourceType(AuditResourceType.INCOME)
				.resourceId(saved.getId())
				.clientId(clientId)
				.beforeState(before)
				.afterState(incomeSnapshot(saved))
				.build());
		return FinanceMapper.toIncomeResponse(saved);
	}

	@Transactional
	public void delete(UUID clientId, UUID incomeId) {
		clientAccessService.requireWriteAccess(clientId);
		Income income = findIncome(clientId, incomeId);
		TransactionStatusRules.assertDraft(income.getStatus());
		periodCloseService.assertPeriodOpen(clientId, income.getTransactionDate());
		Map<String, Object> before = incomeSnapshot(income);
		UUID firmId = income.getFirmId();
		UUID id = income.getId();
		incomeRepository.delete(income);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(firmId)
				.action(AuditAction.INCOME_DELETED)
				.resourceType(AuditResourceType.INCOME)
				.resourceId(id)
				.clientId(clientId)
				.beforeState(before)
				.build());
	}

	@Transactional
	public IncomeResponse approve(UUID clientId, UUID incomeId) {
		clientAccessService.requireApproveAccess(clientId);
		Income income = findIncome(clientId, incomeId);
		TransactionStatusRules.assertCanApprove(income.getStatus());
		periodCloseService.assertPeriodOpen(clientId, income.getTransactionDate());
		if (income.getPaymentMethod() == null) {
			throw new ValidationException("paymentMethod", "Payment method is required before approving income");
		}
		Map<String, Object> before = incomeSnapshot(income);
		income.approve(clientAccessService.requireCurrentUserEntity());
		Income saved = incomeRepository.save(income);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.INCOME_APPROVED)
				.resourceType(AuditResourceType.INCOME)
				.resourceId(saved.getId())
				.clientId(clientId)
				.beforeState(before)
				.afterState(incomeSnapshot(saved))
				.build());
		return FinanceMapper.toIncomeResponse(saved);
	}

	@Transactional
	public IncomeResponse voidIncome(UUID clientId, UUID incomeId, String reason) {
		clientAccessService.requireApproveAccess(clientId);
		Income income = findIncome(clientId, incomeId);
		TransactionStatusRules.assertCanVoid(income.getStatus());
		periodCloseService.assertPeriodOpen(clientId, income.getTransactionDate());
		String voidReason = reason == null ? "" : reason.trim();
		if (voidReason.isBlank()) {
			throw new ValidationException("reason", "Void reason is required");
		}
		Map<String, Object> before = incomeSnapshot(income);
		income.voidIncome(clientAccessService.requireCurrentUserEntity(), voidReason);
		Income saved = incomeRepository.save(income);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.INCOME_VOIDED)
				.resourceType(AuditResourceType.INCOME)
				.resourceId(saved.getId())
				.clientId(clientId)
				.beforeState(before)
				.afterState(incomeSnapshot(saved))
				.build());
		return FinanceMapper.toIncomeResponse(saved);
	}

	private Income findIncome(UUID clientId, UUID incomeId) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		Income income = incomeRepository.findByIdAndClient_IdAndFirmId(incomeId, clientId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("Income", incomeId));
		if (SecurityUtils.requireCurrentUser().getRole() == Role.RoleCode.AUDITOR
				&& !TransactionStatusRules.isFinalized(income.getStatus())) {
			throw new BusinessException(ErrorCodes.ACCESS_DENIED, "Auditors can only view approved or voided income");
		}
		return income;
	}

	private Category requireIncomeCategory(UUID categoryId, Client client) {
		Category category = categoryRepository.findByIdAndFirmIdAndDeletedAtIsNull(categoryId, client.getFirmId())
				.orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
		if (!category.isActive()) {
			throw new BusinessException(ErrorCodes.CATEGORY_INACTIVE, "Category is not active");
		}
		if (category.getCategoryType() == Category.CategoryType.EXPENSE) {
			throw new BusinessException(ErrorCodes.INVALID_CATEGORY, "Category is not valid for income");
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
			throw new ValidationException("taxAmount", "Tax amount cannot exceed income amount");
		}
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

	private Specification<Income> ledgerSpec(
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

	private static Map<String, Object> incomeSnapshot(Income income) {
		Map<String, Object> state = new LinkedHashMap<>();
		state.put("amount", income.getAmount());
		state.put("currencyCode", income.getCurrencyCode());
		state.put("categoryId", income.getCategory() != null ? income.getCategory().getId() : null);
		state.put("transactionDate", income.getTransactionDate() != null
				? income.getTransactionDate().toString() : null);
		state.put("customerName", income.getCustomerName());
		state.put("description", income.getDescription());
		state.put("paymentMethod", income.getPaymentMethod() != null ? income.getPaymentMethod().name() : null);
		state.put("taxAmount", income.getTaxAmount());
		state.put("referenceNo", income.getReferenceNo());
		state.put("status", income.getStatus() != null ? income.getStatus().name() : null);
		state.put("voidReason", income.getVoidReason());
		return state;
	}
}
