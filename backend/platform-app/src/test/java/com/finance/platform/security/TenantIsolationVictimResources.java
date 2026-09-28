package com.finance.platform.security;

import com.finance.platform.support.IntegrationTestSupport;

import java.util.UUID;

/**
 * IDs of resources owned by the victim firm (tenant B) in cross-tenant isolation tests.
 */
public record TenantIsolationVictimResources(
		IntegrationTestSupport.Session victim,
		UUID clientId,
		UUID expenseCategoryId,
		UUID incomeCategoryId,
		UUID expenseId,
		UUID incomeId,
		UUID documentId,
		UUID bankAccountId,
		UUID bankTransactionId,
		UUID periodId,
		UUID arCustomerId,
		UUID arInvoiceId,
		UUID arPaymentId,
		UUID salesInvoiceId
) {
}
