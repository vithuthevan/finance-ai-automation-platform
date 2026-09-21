package com.finance.platform.finance.application.service.invoicing;

import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.finance.application.dto.invoicing.ArAgeingBuckets;
import com.finance.platform.finance.application.dto.invoicing.ArCustomerBalanceResponse;
import com.finance.platform.finance.application.dto.invoicing.ArSummaryResponse;
import com.finance.platform.finance.application.dto.invoicing.SalesInvoiceResponse;
import com.finance.platform.finance.domain.model.invoicing.SalesInvoice;
import com.finance.platform.finance.infrastructure.persistence.ArReportingQueryRepository;
import com.finance.platform.finance.infrastructure.persistence.SalesInvoiceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountsReceivableService {

	private final ArReportingQueryRepository reportingQueryRepository;
	private final SalesInvoiceJpaRepository invoiceRepository;
	private final SalesInvoiceService salesInvoiceService;
	private final InvoiceSettlementService settlementService;

	@Transactional(readOnly = true)
	public ArSummaryResponse summary() {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		BigDecimal totalOutstanding = reportingQueryRepository.sumOutstanding(firmId);
		ArReportingQueryRepository.ArAgeingRow ageing = reportingQueryRepository.ageing(firmId);
		BigDecimal overdue = ageing.days1To30()
				.add(ageing.days31To60())
				.add(ageing.days61To90())
				.add(ageing.days90Plus());
		List<ArCustomerBalanceResponse> topOverdue = reportingQueryRepository.topOverdueCustomers(firmId, 10)
				.stream()
				.map(r -> new ArCustomerBalanceResponse(r.customerId(), r.name(), r.outstanding(), r.overdue()))
				.toList();
		List<SalesInvoiceResponse> open = openInvoices(firmId);
		List<SalesInvoiceResponse> oldest = open.stream()
				.sorted(Comparator.comparing(SalesInvoiceResponse::dueDate, Comparator.nullsLast(Comparator.naturalOrder())))
				.limit(10)
				.toList();
		List<SalesInvoiceResponse> largest = open.stream()
				.sorted(Comparator.comparing(SalesInvoiceResponse::outstanding).reversed())
				.limit(10)
				.toList();
		return new ArSummaryResponse(
				totalOutstanding,
				overdue,
				reportingQueryRepository.dueThisWeek(firmId),
				reportingQueryRepository.collectedThisMonth(firmId),
				new ArAgeingBuckets(
						ageing.current(),
						ageing.days1To30(),
						ageing.days31To60(),
						ageing.days61To90(),
						ageing.days90Plus()
				),
				topOverdue,
				oldest,
				largest
		);
	}

	@Transactional(readOnly = true)
	public PageResponse<SalesInvoiceResponse> receivableInvoices(int page, int size) {
		return salesInvoiceService.list(null, "ISSUED", null, null, page, size);
	}

	@Transactional(readOnly = true)
	public ArAgeingBuckets ageing() {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		ArReportingQueryRepository.ArAgeingRow row = reportingQueryRepository.ageing(firmId);
		return new ArAgeingBuckets(row.current(), row.days1To30(), row.days31To60(), row.days61To90(), row.days90Plus());
	}

	@Transactional(readOnly = true)
	public List<ArCustomerBalanceResponse> customerBalances() {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		return reportingQueryRepository.topOverdueCustomers(firmId, 100)
				.stream()
				.map(r -> new ArCustomerBalanceResponse(r.customerId(), r.name(), r.outstanding(), r.overdue()))
				.toList();
	}

	@Transactional(readOnly = true)
	public PageResponse<SalesInvoiceResponse> overdue(int page, int size) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		List<SalesInvoice> invoices = invoiceRepository.findByFirmIdOrderByCreatedAtDesc(firmId);
		List<SalesInvoiceResponse> overdue = invoices.stream()
				.filter(i -> i.getStatus() == SalesInvoice.DocumentStatus.ISSUED)
				.filter(i -> i.getDueDate() != null && i.getDueDate().isBefore(LocalDate.now()))
				.filter(i -> settlementService.outstanding(i).compareTo(BigDecimal.ZERO) > 0)
				.map(i -> salesInvoiceService.get(i.getId()))
				.toList();
		int from = Math.min(page * size, overdue.size());
		int to = Math.min(from + size, overdue.size());
		return new PageResponse<>(overdue.subList(from, to), page, size, overdue.size());
	}

	private List<SalesInvoiceResponse> openInvoices(UUID firmId) {
		return invoiceRepository.findByFirmIdOrderByCreatedAtDesc(firmId).stream()
				.filter(i -> i.getStatus() == SalesInvoice.DocumentStatus.ISSUED)
				.filter(i -> settlementService.outstanding(i).compareTo(BigDecimal.ZERO) > 0)
				.map(i -> salesInvoiceService.get(i.getId()))
				.toList();
	}
}
