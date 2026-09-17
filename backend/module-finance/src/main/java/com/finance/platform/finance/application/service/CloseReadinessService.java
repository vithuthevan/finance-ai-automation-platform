package com.finance.platform.finance.application.service;

import com.finance.platform.finance.application.close.CloseCheck;
import com.finance.platform.finance.application.close.CloseCheckContext;
import com.finance.platform.finance.application.close.CloseCheckSeverity;
import com.finance.platform.finance.application.close.CloseFinding;
import com.finance.platform.finance.application.dto.CloseChecklistItemResponse;
import com.finance.platform.finance.application.dto.CloseFindingResponse;
import com.finance.platform.finance.application.dto.PeriodDocumentSummaryResponse;
import com.finance.platform.finance.application.dto.PeriodLedgerSummaryResponse;
import com.finance.platform.finance.application.dto.PeriodReadinessResponse;
import com.finance.platform.finance.application.dto.PeriodReadinessSummaryResponse;
import com.finance.platform.finance.domain.model.Receipt;
import com.finance.platform.finance.infrastructure.persistence.BankAccountJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.BankTransactionJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.CloseReadinessQueryRepository;
import com.finance.platform.finance.infrastructure.persistence.CloseReadinessQueryRepository.StatusMoney;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Close readiness is evidence-driven, not a status dropdown.
 * <p>
 * Percentage formula (deterministic):
 * 100 if and only if there are no BLOCKERs (warnings may still exist).
 * Otherwise {@code max(0, 100 - min(90, totalBlockerItemCount * 8))}.
 * Each unresolved blocker item reduces the score by 8 points, capped at a 90-point deduction
 * so a period with remaining work still shows a non-zero score.
 */
@Service
@RequiredArgsConstructor
public class CloseReadinessService {

	private final List<CloseCheck> checks;
	private final CloseReadinessQueryRepository queries;
	private final BankAccountJpaRepository bankAccountRepository;
	private final BankTransactionJpaRepository bankTransactionRepository;

	public PeriodReadinessResponse evaluate(UUID firmId, UUID clientId, UUID periodId, LocalDate from, LocalDate to) {
		CloseCheckContext context = new CloseCheckContext(firmId, clientId, periodId, from, to);
		List<CloseFinding> findings = new ArrayList<>();
		List<CloseChecklistItemResponse> checklist = new ArrayList<>();
		for (CloseCheck check : checks) {
			List<CloseFinding> result = check.enabled() ? check.evaluate(context) : List.of();
			findings.addAll(result);
			int count = result.stream().mapToInt(CloseFinding::count).sum();
			boolean passed = !check.enabled() || count == 0;
			checklist.add(new CloseChecklistItemResponse(
					check.code(),
					check.label(),
					passed,
					check.defaultSeverity(),
					count
			));
		}

		List<CloseFindingResponse> blockers = map(findings, CloseCheckSeverity.BLOCKER);
		List<CloseFindingResponse> warnings = map(findings, CloseCheckSeverity.WARNING);
		List<CloseFindingResponse> info = map(findings, CloseCheckSeverity.INFO);
		boolean ready = blockers.isEmpty();
		int blockerItems = blockers.stream().mapToInt(CloseFindingResponse::count).sum();
		int percent = ready ? 100 : Math.max(0, 100 - Math.min(90, blockerItems * 8));

		Map<String, StatusMoney> expenses = queries.ledgerByStatus("expenses", firmId, clientId, from, to);
		Map<String, StatusMoney> income = queries.ledgerByStatus("income", firmId, clientId, from, to);
		StatusMoney draftExp = expenses.getOrDefault("DRAFT", new StatusMoney(0, BigDecimal.ZERO));
		StatusMoney approvedExp = expenses.getOrDefault("APPROVED", new StatusMoney(0, BigDecimal.ZERO));
		StatusMoney voidExp = expenses.getOrDefault("VOID", new StatusMoney(0, BigDecimal.ZERO));
		StatusMoney draftInc = income.getOrDefault("DRAFT", new StatusMoney(0, BigDecimal.ZERO));
		StatusMoney approvedInc = income.getOrDefault("APPROVED", new StatusMoney(0, BigDecimal.ZERO));
		StatusMoney voidInc = income.getOrDefault("VOID", new StatusMoney(0, BigDecimal.ZERO));

		Map<String, Long> docStatus = queries.documentStatusCounts(firmId, clientId, from, to);
		long totalDocs = queries.countDocumentsInPeriod(firmId, clientId, from, to);
		long linkedDocs = queries.countLinkedDocuments(firmId, clientId, from, to);
		long needsReview = queries.countDocumentsNeedingReview(firmId, clientId, from, to);
		long failedUnlinked = queries.countFailedUnlinked(firmId, clientId, from, to);
		long unlinked = queries.countUnlinkedFinancial(firmId, clientId, from, to);
		long openRequests = queries.countOpenDocumentRequests(firmId, clientId, periodId, from, to);
		long unsupportedExpenses = queries.countApprovedWithoutDocuments("expenses", firmId, clientId, from, to);
		long unsupportedIncome = queries.countApprovedWithoutDocuments("income", firmId, clientId, from, to);
		long supportedExpenses = queries.countApprovedWithDocuments("expenses", firmId, clientId, from, to);
		long supportedIncome = queries.countApprovedWithDocuments("income", firmId, clientId, from, to);

		long bankAccounts = bankAccountRepository.countByClient_IdAndActiveTrue(clientId);
		long bankTransactions = bankTransactionRepository.countImportedInPeriod(clientId, from, to);
		long matchedBank = bankTransactionRepository.countByClientAndPeriodAndStatus(
				clientId, from, to, com.finance.platform.finance.domain.model.BankTransaction.MatchStatus.MATCHED);
		long unmatchedBank = bankTransactionRepository.countByClientAndPeriodAndStatus(
				clientId, from, to, com.finance.platform.finance.domain.model.BankTransaction.MatchStatus.UNMATCHED)
				+ bankTransactionRepository.countByClientAndPeriodAndStatus(
				clientId, from, to, com.finance.platform.finance.domain.model.BankTransaction.MatchStatus.SUGGESTED)
				+ bankTransactionRepository.countByClientAndPeriodAndStatus(
				clientId, from, to, com.finance.platform.finance.domain.model.BankTransaction.MatchStatus.PENDING_APPROVAL);
		Integer reconciliationPercent = null;
		if (bankTransactions > 0) {
			long ignored = bankTransactionRepository.countByClientAndPeriodAndStatus(
					clientId, from, to, com.finance.platform.finance.domain.model.BankTransaction.MatchStatus.IGNORED);
			reconciliationPercent = com.finance.platform.finance.application.banking.ReconciliationProgressPercent.compute(
					bankTransactions, matchedBank, ignored);
		}

		return new PeriodReadinessResponse(
				ready,
				percent,
				blockers,
				warnings,
				info,
				checklist,
				new PeriodReadinessSummaryResponse(
						draftExp.count(),
						draftInc.count(),
						needsReview,
						failedUnlinked,
						unlinked,
						openRequests,
						unsupportedExpenses + unsupportedIncome,
						supportedExpenses + supportedIncome,
						bankAccounts,
						bankTransactions,
						matchedBank,
						unmatchedBank,
						reconciliationPercent
				),
				new PeriodLedgerSummaryResponse(
						draftExp.count(),
						approvedExp.count(),
						voidExp.count(),
						draftExp.amount(),
						approvedExp.amount(),
						draftInc.count(),
						approvedInc.count(),
						voidInc.count(),
						draftInc.amount(),
						approvedInc.amount(),
						approvedInc.amount().subtract(approvedExp.amount())
				),
				new PeriodDocumentSummaryResponse(
						totalDocs,
						linkedDocs,
						Math.max(0, totalDocs - linkedDocs),
						needsReview,
						docStatus.getOrDefault(Receipt.ReceiptStatus.REJECTED.name(), 0L),
						docStatus.getOrDefault(Receipt.ReceiptStatus.FAILED.name(), 0L),
						docStatus.getOrDefault(Receipt.ReceiptStatus.LINKED.name(), 0L)
								+ docStatus.getOrDefault(Receipt.ReceiptStatus.NEEDS_REVIEW.name(), 0L)
				)
		);
	}

	private static List<CloseFindingResponse> map(List<CloseFinding> findings, CloseCheckSeverity severity) {
		return findings.stream()
				.filter(finding -> finding.severity() == severity)
				.map(finding -> new CloseFindingResponse(
						finding.severity(),
						finding.code(),
						finding.message(),
						finding.count(),
						finding.actionHint()))
				.toList();
	}
}
