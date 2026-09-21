package com.finance.platform.finance.application.service.invoicing;

import com.finance.platform.finance.application.invoicing.MoneyMath;
import com.finance.platform.finance.domain.model.invoicing.SalesInvoice;
import com.finance.platform.finance.infrastructure.persistence.ArPaymentAllocationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceSettlementService {

	private final ArPaymentAllocationJpaRepository allocationRepository;

	@Transactional(readOnly = true)
	public BigDecimal allocatedTotal(UUID invoiceId) {
		return MoneyMath.money(allocationRepository.sumActiveAllocatedToInvoice(invoiceId));
	}

	@Transactional(readOnly = true)
	public BigDecimal outstanding(SalesInvoice invoice) {
		if (invoice.getStatus() != SalesInvoice.DocumentStatus.ISSUED) {
			return BigDecimal.ZERO.setScale(MoneyMath.SCALE, MoneyMath.ROUNDING);
		}
		BigDecimal allocated = allocatedTotal(invoice.getId());
		return MoneyMath.subtract(invoice.getTotal(), allocated).max(BigDecimal.ZERO);
	}

	@Transactional
	public void refreshSettlement(SalesInvoice invoice) {
		if (invoice.getStatus() != SalesInvoice.DocumentStatus.ISSUED) {
			return;
		}
		BigDecimal outstanding = outstanding(invoice);
		if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
			invoice.setSettlementStatus(SalesInvoice.SettlementStatus.PAID);
		} else {
			BigDecimal allocated = allocatedTotal(invoice.getId());
			if (allocated.compareTo(BigDecimal.ZERO) > 0) {
				invoice.setSettlementStatus(SalesInvoice.SettlementStatus.PARTIALLY_PAID);
			} else {
				invoice.setSettlementStatus(SalesInvoice.SettlementStatus.UNPAID);
			}
			if (invoice.getDueDate() != null
					&& invoice.getDueDate().isBefore(LocalDate.now())
					&& invoice.getSettlementStatus() != SalesInvoice.SettlementStatus.PAID) {
				invoice.setSettlementStatus(SalesInvoice.SettlementStatus.OVERDUE);
			}
		}
	}
}
