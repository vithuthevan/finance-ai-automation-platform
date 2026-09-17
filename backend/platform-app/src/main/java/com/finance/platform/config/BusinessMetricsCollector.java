package com.finance.platform.config;

import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.finance.domain.model.Receipt;
import com.finance.platform.finance.infrastructure.persistence.ExpenseJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.FirmJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ReceiptJpaRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class BusinessMetricsCollector {

	private final MeterRegistry meterRegistry;
	private final FirmJpaRepository firmRepository;
	private final UserJpaRepository userRepository;
	private final ReceiptJpaRepository receiptRepository;
	private final ExpenseJpaRepository expenseRepository;

	private final AtomicLong activeFirms = new AtomicLong();
	private final AtomicLong activeUsers = new AtomicLong();
	private final AtomicLong documentsUploaded = new AtomicLong();
	private final AtomicLong expensesCreated = new AtomicLong();
	private final AtomicLong failedDocumentProcessing = new AtomicLong();

	@Scheduled(fixedRateString = "${app.observability.metrics-refresh-ms:60000}")
	@Transactional(readOnly = true)
	public void refreshBusinessGauges() {
		activeFirms.set(firmRepository.count());
		activeUsers.set(userRepository.countByDeletedAtIsNull());
		documentsUploaded.set(receiptRepository.countByDeletedAtIsNull());
		expensesCreated.set(expenseRepository.count());
		failedDocumentProcessing.set(receiptRepository.countByStatusAndDeletedAtIsNull(Receipt.ReceiptStatus.FAILED));
	}

	@PostConstruct
	public void registerGauges() {
		registerGauge("finance.firms.active", activeFirms);
		registerGauge("finance.users.active", activeUsers);
		registerGauge("finance.documents.uploaded", documentsUploaded);
		registerGauge("finance.expenses.created", expensesCreated);
		registerGauge("finance.documents.processing.failed", failedDocumentProcessing);
		refreshBusinessGauges();
	}

	private void registerGauge(String name, AtomicLong holder) {
		Gauge.builder(name, holder, AtomicLong::get)
				.description("Business metric refreshed periodically from the database")
				.register(meterRegistry);
	}
}
