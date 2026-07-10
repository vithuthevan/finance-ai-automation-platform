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
import com.finance.platform.finance.application.dto.IncomeRequest;
import com.finance.platform.finance.application.dto.IncomeResponse;
import com.finance.platform.finance.application.mapper.FinanceMapper;
import com.finance.platform.finance.domain.model.Category;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.domain.model.Income;
import com.finance.platform.finance.domain.model.TransactionSource;
import com.finance.platform.finance.domain.model.TransactionStatus;
import com.finance.platform.finance.infrastructure.persistence.CategoryJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.IncomeJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncomeService {

	private final IncomeJpaRepository incomeRepository;
	private final CategoryJpaRepository categoryRepository;
	private final ClientAccessService clientAccessService;
	private final AuditLogger auditLogger;

	@Transactional(readOnly = true)
	public PageResponse<IncomeResponse> list(UUID clientId, TransactionStatus status, int page, int size) {
		clientAccessService.requireAccessibleClient(clientId);
		TransactionStatus effectiveStatus = resolveListStatus(status);

		PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate"));
		Page<Income> results = effectiveStatus == null
				? incomeRepository.findByClientId(clientId, pageable)
				: incomeRepository.findByClientIdAndStatus(clientId, effectiveStatus, pageable);

		return new PageResponse<>(
				results.map(FinanceMapper::toIncomeResponse).getContent(),
				results.getNumber(),
				results.getSize(),
				results.getTotalElements()
		);
	}

	@Transactional(readOnly = true)
	public IncomeResponse get(UUID clientId, UUID incomeId) {
		clientAccessService.requireAccessibleClient(clientId);
		return FinanceMapper.toIncomeResponse(findIncome(clientId, incomeId));
	}

	@Transactional
	public IncomeResponse create(UUID clientId, IncomeRequest request) {
		Client client = clientAccessService.requireAccessibleClient(clientId);
		User currentUser = clientAccessService.requireCurrentUserEntity();
		Category category = requireIncomeCategory(request.categoryId(), client.getFirmId());

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
		Client client = clientAccessService.requireAccessibleClient(clientId);
		Income income = findIncome(clientId, incomeId);
		ensureDraft(income);
		Map<String, Object> before = incomeSnapshot(income);

		Category category = requireIncomeCategory(request.categoryId(), client.getFirmId());
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
		clientAccessService.requireAccessibleClient(clientId);
		Income income = findIncome(clientId, incomeId);
		ensureDraft(income);
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
		clientAccessService.requireAccessibleClient(clientId);
		assertCanApprove();
		Income income = findIncome(clientId, incomeId);
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

	private Income findIncome(UUID clientId, UUID incomeId) {
		Income income = incomeRepository.findByIdAndClientId(incomeId, clientId)
				.orElseThrow(() -> new ResourceNotFoundException("Income", incomeId));
		if (SecurityUtils.requireCurrentUser().getRole() == Role.RoleCode.AUDITOR
				&& income.getStatus() != TransactionStatus.APPROVED) {
			throw new BusinessException("Auditors can only view approved income");
		}
		return income;
	}

	private Category requireIncomeCategory(UUID categoryId, UUID firmId) {
		Category category = categoryRepository.findByIdAndFirmId(categoryId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
		if (category.getCategoryType() == Category.CategoryType.EXPENSE) {
			throw new ValidationException("categoryId", "Category is not valid for income");
		}
		return category;
	}

	private void ensureDraft(Income income) {
		if (income.getStatus() != TransactionStatus.DRAFT) {
			throw new BusinessException("Only draft income can be modified or deleted");
		}
	}

	private void assertCanApprove() {
		Role.RoleCode role = SecurityUtils.requireCurrentUser().getRole();
		if (role != Role.RoleCode.ADMIN && role != Role.RoleCode.ACCOUNTANT) {
			throw new BusinessException("Only administrators and accountants can approve income");
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
		return state;
	}
}
