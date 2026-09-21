package com.finance.platform.finance.application.service.invoicing;

import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.finance.application.dto.invoicing.AllocatePaymentRequest;
import com.finance.platform.finance.application.dto.invoicing.ArPaymentRequest;
import com.finance.platform.finance.application.dto.invoicing.ArPaymentResponse;
import com.finance.platform.finance.application.dto.invoicing.PaymentAllocationItemRequest;
import com.finance.platform.finance.application.dto.invoicing.PaymentAllocationResponse;
import com.finance.platform.finance.application.dto.invoicing.ReverseAllocationRequest;
import com.finance.platform.finance.application.dto.invoicing.ReversePaymentRequest;
import com.finance.platform.finance.application.invoicing.MoneyMath;
import com.finance.platform.finance.domain.model.invoicing.ArPayment;
import com.finance.platform.finance.domain.model.invoicing.ArPaymentAllocation;
import com.finance.platform.finance.domain.model.invoicing.SalesInvoice;
import com.finance.platform.finance.infrastructure.persistence.ArPaymentAllocationJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ArPaymentJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.SalesInvoiceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArPaymentService {

	private final ArPaymentJpaRepository paymentRepository;
	private final ArPaymentAllocationJpaRepository allocationRepository;
	private final SalesInvoiceJpaRepository invoiceRepository;
	private final InvoiceSettlementService settlementService;
	private final AuditLogger auditLogger;

	@Transactional(readOnly = true)
	public List<ArPaymentResponse> list() {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		return paymentRepository.findByFirmIdOrderByPaymentDateDescCreatedAtDesc(firmId)
				.stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public ArPaymentResponse get(UUID paymentId) {
		return toResponse(requirePayment(paymentId));
	}

	@Transactional
	public ArPaymentResponse record(ArPaymentRequest request) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		BigDecimal amount = MoneyMath.money(request.amount());
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new ValidationException("amount", "Payment amount must be positive");
		}
		ArPayment.Source source = ArPayment.Source.MANUAL;
		if (request.source() != null && !request.source().isBlank()) {
			try {
				source = ArPayment.Source.valueOf(request.source().trim().toUpperCase());
			} catch (IllegalArgumentException ex) {
				throw new ValidationException("source", "Invalid payment source");
			}
		}
		ArPayment payment = ArPayment.builder()
				.customerId(request.customerId())
				.paymentDate(request.paymentDate())
				.amount(amount)
				.reference(request.reference())
				.unallocatedAmount(amount)
				.status(ArPayment.Status.RECEIVED)
				.source(source)
				.build();
		payment.setFirmId(firmId);
		payment = paymentRepository.save(payment);
		auditLogger.record(AuditEvent.fromTenant()
				.action(AuditAction.INCOME_CREATED)
				.resourceType(AuditResourceType.INCOME)
				.resourceId(payment.getId())
				.metadata(Map.of("event", "ar_payment_recorded"))
				.build());
		return toResponse(payment);
	}

	@Transactional
	public ArPaymentResponse allocate(UUID paymentId, AllocatePaymentRequest request) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		ArPayment payment = paymentRepository.findForUpdate(paymentId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("ArPayment", paymentId));
		if (payment.getStatus() == ArPayment.Status.REVERSED) {
			throw new BusinessException(ErrorCodes.VALIDATION_FAILED, "Cannot allocate a reversed payment");
		}
		BigDecimal requestTotal = BigDecimal.ZERO;
		Map<UUID, BigDecimal> byInvoice = new HashMap<>();
		for (PaymentAllocationItemRequest item : request.allocations()) {
			BigDecimal amt = MoneyMath.money(item.amount());
			requestTotal = MoneyMath.add(requestTotal, amt);
			byInvoice.merge(item.invoiceId(), amt, MoneyMath::add);
		}
		if (requestTotal.compareTo(payment.getUnallocatedAmount()) > 0) {
			throw new ValidationException("allocations", "Allocation total exceeds unallocated payment amount");
		}
		for (Map.Entry<UUID, BigDecimal> entry : byInvoice.entrySet()) {
			applyAllocation(payment, entry.getKey(), entry.getValue());
		}
		refreshPaymentStatus(payment);
		payment = paymentRepository.save(payment);
		return toResponse(payment);
	}

	@Transactional
	public ArPaymentResponse reverseAllocation(UUID paymentId, UUID allocationId, ReverseAllocationRequest request) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		ArPayment payment = paymentRepository.findForUpdate(paymentId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("ArPayment", paymentId));
		if (payment.getStatus() == ArPayment.Status.REVERSED) {
			throw new BusinessException(ErrorCodes.VALIDATION_FAILED, "Cannot reverse allocation on a reversed payment");
		}
		ArPaymentAllocation allocation = allocationRepository.findByPayment_Id(payment.getId()).stream()
				.filter(row -> row.getId().equals(allocationId))
				.findFirst()
				.orElseThrow(() -> new ResourceNotFoundException("ArPaymentAllocation", allocationId));
		if (!allocation.isActive()) {
			return toResponse(payment);
		}
		BigDecimal amount = allocation.getAmount();
		allocation.setActive(false);
		allocationRepository.save(allocation);
		payment.setUnallocatedAmount(MoneyMath.add(payment.getUnallocatedAmount(), amount));
		invoiceRepository.findForUpdate(allocation.getInvoiceId(), firmId).ifPresent(invoice -> {
			settlementService.refreshSettlement(invoice);
			invoiceRepository.save(invoice);
		});
		refreshPaymentStatus(payment);
		payment = paymentRepository.save(payment);
		auditLogger.record(AuditEvent.fromTenant()
				.action(AuditAction.INCOME_VOIDED)
				.resourceType(AuditResourceType.INCOME)
				.resourceId(payment.getId())
				.metadata(Map.of(
						"event", "ar_allocation_reversed",
						"allocationId", allocationId.toString(),
						"reason", request.reason() == null ? "" : request.reason()))
				.build());
		return toResponse(payment);
	}

	@Transactional
	public ArPaymentResponse recordFromBank(
			UUID customerId,
			LocalDate paymentDate,
			BigDecimal amount,
			String reference,
			UUID bankTransactionId
	) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		if (bankTransactionId != null) {
			var existing = paymentRepository.findByFirmIdAndBankTransactionIdAndStatusNot(
					firmId, bankTransactionId, ArPayment.Status.REVERSED);
			if (existing.isPresent()) {
				return toResponse(existing.get());
			}
		}
		BigDecimal money = MoneyMath.money(amount);
		ArPayment payment = ArPayment.builder()
				.customerId(customerId)
				.paymentDate(paymentDate)
				.amount(money)
				.reference(reference)
				.unallocatedAmount(money)
				.status(ArPayment.Status.RECEIVED)
				.source(ArPayment.Source.BANK_IMPORT)
				.bankTransactionId(bankTransactionId)
				.build();
		payment.setFirmId(firmId);
		try {
			payment = paymentRepository.save(payment);
		} catch (org.springframework.dao.DataIntegrityViolationException ex) {
			if (bankTransactionId != null) {
				return toResponse(paymentRepository.findByFirmIdAndBankTransactionIdAndStatusNot(
								firmId, bankTransactionId, ArPayment.Status.REVERSED)
						.orElseThrow(() -> ex));
			}
			throw ex;
		}
		return toResponse(payment);
	}

	@Transactional
	public ArPaymentResponse reverse(UUID paymentId, ReversePaymentRequest request) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		UUID userId = SecurityUtils.requireCurrentUser().getId();
		ArPayment payment = paymentRepository.findForUpdate(paymentId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("ArPayment", paymentId));
		if (payment.getStatus() == ArPayment.Status.REVERSED) {
			return toResponse(payment);
		}
		List<ArPaymentAllocation> allocations = allocationRepository.findByPayment_Id(payment.getId());
		for (ArPaymentAllocation allocation : allocations) {
			if (!allocation.isActive()) {
				continue;
			}
			allocation.setActive(false);
			allocationRepository.save(allocation);
			invoiceRepository.findForUpdate(allocation.getInvoiceId(), firmId).ifPresent(invoice -> {
				settlementService.refreshSettlement(invoice);
				invoiceRepository.save(invoice);
			});
		}
		payment.setStatus(ArPayment.Status.REVERSED);
		payment.setUnallocatedAmount(BigDecimal.ZERO.setScale(MoneyMath.SCALE, MoneyMath.ROUNDING));
		payment.setReversedAt(Instant.now());
		payment.setReversalReason(request.reason());
		payment.setReversedBy(userId);
		payment = paymentRepository.save(payment);
		auditLogger.record(AuditEvent.fromTenant()
				.action(AuditAction.INCOME_VOIDED)
				.resourceType(AuditResourceType.INCOME)
				.resourceId(payment.getId())
				.metadata(Map.of("event", "ar_payment_reversed", "reason", request.reason()))
				.build());
		return toResponse(payment);
	}

	private void applyAllocation(ArPayment payment, UUID invoiceId, BigDecimal amount) {
		UUID firmId = payment.getFirmId();
		SalesInvoice invoice = invoiceRepository.findForUpdate(invoiceId, firmId)
				.orElseThrow(() -> new ValidationException("invoiceId", "Invoice not found"));
		if (invoice.getStatus() != SalesInvoice.DocumentStatus.ISSUED) {
			throw new ValidationException("invoiceId", "Cannot allocate to non-issued invoice");
		}
		BigDecimal outstanding = settlementService.outstanding(invoice);
		if (amount.compareTo(outstanding) > 0) {
			throw new ValidationException("amount", "Allocation exceeds invoice outstanding balance");
		}
		ArPaymentAllocation allocation = allocationRepository.findByPayment_Id(payment.getId()).stream()
				.filter(a -> a.getInvoiceId().equals(invoiceId))
				.findFirst()
				.orElse(null);
		if (allocation == null) {
			allocation = ArPaymentAllocation.builder()
					.payment(payment)
					.invoiceId(invoiceId)
					.amount(amount)
					.active(true)
					.build();
		} else {
			BigDecimal base = allocation.isActive() ? allocation.getAmount() : BigDecimal.ZERO;
			allocation.setAmount(MoneyMath.add(base, amount));
			allocation.setActive(true);
		}
		allocationRepository.save(allocation);
		payment.setUnallocatedAmount(MoneyMath.subtract(payment.getUnallocatedAmount(), amount));
		settlementService.refreshSettlement(invoice);
		invoiceRepository.save(invoice);
	}

	private void refreshPaymentStatus(ArPayment payment) {
		if (payment.getUnallocatedAmount().compareTo(BigDecimal.ZERO) <= 0) {
			payment.setStatus(ArPayment.Status.ALLOCATED);
			payment.setUnallocatedAmount(BigDecimal.ZERO.setScale(MoneyMath.SCALE, MoneyMath.ROUNDING));
		} else {
			BigDecimal allocated = allocationRepository.sumActiveAllocatedOnPayment(payment.getId());
			payment.setStatus(allocated.compareTo(BigDecimal.ZERO) > 0
					? ArPayment.Status.PARTIALLY_ALLOCATED
					: ArPayment.Status.RECEIVED);
		}
	}

	private ArPayment requirePayment(UUID paymentId) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		return paymentRepository.findByIdAndFirmId(paymentId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("ArPayment", paymentId));
	}

	private ArPaymentResponse toResponse(ArPayment payment) {
		List<PaymentAllocationResponse> allocations = new ArrayList<>();
		for (ArPaymentAllocation allocation : allocationRepository.findByPayment_Id(payment.getId())) {
			String invoiceNumber = invoiceRepository.findById(allocation.getInvoiceId())
					.map(SalesInvoice::getInvoiceNumber)
					.orElse(null);
			allocations.add(new PaymentAllocationResponse(
					allocation.getId(),
					allocation.getInvoiceId(),
					invoiceNumber,
					allocation.getAmount(),
					allocation.isActive()
			));
		}
		return new ArPaymentResponse(
				payment.getId(),
				payment.getCustomerId(),
				payment.getPaymentDate(),
				payment.getAmount(),
				payment.getUnallocatedAmount(),
				payment.getReference(),
				payment.getStatus().name(),
				payment.getSource().name(),
				payment.getRowVersion(),
				payment.getCreatedAt(),
				allocations
		);
	}
}
