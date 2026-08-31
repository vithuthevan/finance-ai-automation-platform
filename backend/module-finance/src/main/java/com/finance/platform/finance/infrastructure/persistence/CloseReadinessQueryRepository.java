package com.finance.platform.finance.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Period-scoped close counts. Document accounting date prefers linked transaction date,
 * then AI/manual suggested_date, then upload date (last resort; upload time is not an
 * accounting date).
 */
@Repository
@RequiredArgsConstructor
public class CloseReadinessQueryRepository {

	static final String DOCUMENT_ACCOUNTING_DATE = """
			coalesce(
			  (select min(e.transaction_date) from expense_receipts er join expenses e on e.id = er.expense_id where er.receipt_id = r.id),
			  (select min(i.transaction_date) from income_receipts ir join income i on i.id = ir.income_id where ir.receipt_id = r.id),
			  r.suggested_date,
			  (r.uploaded_at at time zone 'UTC')::date
			)
			""";

	private static final String FINANCIAL_TYPES = "('RECEIPT', 'INVOICE', 'PURCHASE_INVOICE', 'SALES_INVOICE', 'CREDIT_NOTE')";

	private final EntityManager entityManager;

	public long countByStatus(String table, UUID firmId, UUID clientId, String status, LocalDate from, LocalDate to) {
		Query query = entityManager.createNativeQuery(
				"select count(*) from " + table
						+ " where firm_id = :firmId and client_id = :clientId and status = :status"
						+ " and transaction_date between :from and :to");
		bindScope(query, firmId, clientId, from, to);
		query.setParameter("status", status);
		return toLong(query.getSingleResult());
	}

	public BigDecimal sumByStatus(String table, UUID firmId, UUID clientId, String status, LocalDate from, LocalDate to) {
		Query query = entityManager.createNativeQuery(
				"select coalesce(sum(amount), 0) from " + table
						+ " where firm_id = :firmId and client_id = :clientId and status = :status"
						+ " and transaction_date between :from and :to");
		bindScope(query, firmId, clientId, from, to);
		query.setParameter("status", status);
		return toMoney(query.getSingleResult());
	}

	public Map<String, StatusMoney> ledgerByStatus(String table, UUID firmId, UUID clientId, LocalDate from, LocalDate to) {
		Query query = entityManager.createNativeQuery(
				"select status, count(*), coalesce(sum(amount), 0) from " + table
						+ " where firm_id = :firmId and client_id = :clientId"
						+ " and transaction_date between :from and :to group by status");
		bindScope(query, firmId, clientId, from, to);
		Map<String, StatusMoney> rows = new LinkedHashMap<>();
		for (Object raw : query.getResultList()) {
			Object[] cols = (Object[]) raw;
			rows.put((String) cols[0], new StatusMoney(toLong(cols[1]), toMoney(cols[2])));
		}
		return rows;
	}

	public long countDocumentsNeedingReview(UUID firmId, UUID clientId, LocalDate from, LocalDate to) {
		return countDocuments(firmId, clientId, from, to,
				"r.status in ('NEEDS_REVIEW', 'PROCESSING', 'EXTRACTED')");
	}

	public long countFailedUnlinked(UUID firmId, UUID clientId, LocalDate from, LocalDate to) {
		return countDocuments(firmId, clientId, from, to,
				"r.status = 'FAILED' and not " + linkedPredicate());
	}

	public long countUnlinkedFinancial(UUID firmId, UUID clientId, LocalDate from, LocalDate to) {
		return countDocuments(firmId, clientId, from, to,
				"r.document_type in " + FINANCIAL_TYPES
						+ " and r.status not in ('LINKED', 'REJECTED', 'FAILED')"
						+ " and not " + linkedPredicate());
	}

	public long countOpenDocumentRequests(UUID firmId, UUID clientId, UUID periodId, LocalDate from, LocalDate to) {
		String sql;
		if (periodId == null) {
			sql = """
					select count(*) from document_requests
					where firm_id = :firmId and client_id = :clientId
					  and status in ('OPEN', 'UPLOADED')
					  and (due_date between :from and :to or due_date is null)
					""";
		} else {
			sql = """
					select count(*) from document_requests
					where firm_id = :firmId and client_id = :clientId
					  and status in ('OPEN', 'UPLOADED')
					  and (period_id = :periodId or period_id is null)
					""";
		}
		Query query = entityManager.createNativeQuery(sql);
		query.setParameter("firmId", firmId);
		query.setParameter("clientId", clientId);
		if (periodId == null) {
			query.setParameter("from", from);
			query.setParameter("to", to);
		} else {
			query.setParameter("periodId", periodId);
		}
		return toLong(query.getSingleResult());
	}

	public long countApprovedWithoutDocuments(String table, UUID firmId, UUID clientId, LocalDate from, LocalDate to) {
		String join = "expenses".equals(table)
				? "not exists (select 1 from expense_receipts er where er.expense_id = t.id)"
				: "not exists (select 1 from income_receipts ir where ir.income_id = t.id)";
		Query query = entityManager.createNativeQuery(
				"select count(*) from " + table + " t"
						+ " where t.firm_id = :firmId and t.client_id = :clientId and t.status = 'APPROVED'"
						+ " and t.transaction_date between :from and :to and " + join);
		bindScope(query, firmId, clientId, from, to);
		return toLong(query.getSingleResult());
	}

	public long countApprovedWithDocuments(String table, UUID firmId, UUID clientId, LocalDate from, LocalDate to) {
		String join = "expenses".equals(table)
				? "exists (select 1 from expense_receipts er where er.expense_id = t.id)"
				: "exists (select 1 from income_receipts ir where ir.income_id = t.id)";
		Query query = entityManager.createNativeQuery(
				"select count(*) from " + table + " t"
						+ " where t.firm_id = :firmId and t.client_id = :clientId and t.status = 'APPROVED'"
						+ " and t.transaction_date between :from and :to and " + join);
		bindScope(query, firmId, clientId, from, to);
		return toLong(query.getSingleResult());
	}

	public Map<String, Long> documentStatusCounts(UUID firmId, UUID clientId, LocalDate from, LocalDate to) {
		Query query = entityManager.createNativeQuery(
				"select r.status, count(*) from receipts r where r.firm_id = :firmId and r.client_id = :clientId"
						+ " and r.deleted_at is null and " + DOCUMENT_ACCOUNTING_DATE
						+ " between :from and :to group by r.status");
		bindScope(query, firmId, clientId, from, to);
		Map<String, Long> rows = new LinkedHashMap<>();
		for (Object raw : query.getResultList()) {
			Object[] cols = (Object[]) raw;
			rows.put((String) cols[0], toLong(cols[1]));
		}
		return rows;
	}

	public long countDocumentsInPeriod(UUID firmId, UUID clientId, LocalDate from, LocalDate to) {
		return countDocuments(firmId, clientId, from, to, "1=1");
	}

	public long countLinkedDocuments(UUID firmId, UUID clientId, LocalDate from, LocalDate to) {
		return countDocuments(firmId, clientId, from, to, linkedPredicate());
	}

	private long countDocuments(UUID firmId, UUID clientId, LocalDate from, LocalDate to, String extraWhere) {
		Query query = entityManager.createNativeQuery(
				"select count(*) from receipts r where r.firm_id = :firmId and r.client_id = :clientId"
						+ " and r.deleted_at is null and " + DOCUMENT_ACCOUNTING_DATE
						+ " between :from and :to and (" + extraWhere + ")");
		bindScope(query, firmId, clientId, from, to);
		return toLong(query.getSingleResult());
	}

	private static String linkedPredicate() {
		return "(exists (select 1 from expense_receipts er where er.receipt_id = r.id)"
				+ " or exists (select 1 from income_receipts ir where ir.receipt_id = r.id)"
				+ " or r.status = 'LINKED')";
	}

	private static void bindScope(Query query, UUID firmId, UUID clientId, LocalDate from, LocalDate to) {
		query.setParameter("firmId", firmId);
		query.setParameter("clientId", clientId);
		query.setParameter("from", from);
		query.setParameter("to", to);
	}

	private static long toLong(Object value) {
		return value == null ? 0L : ((Number) value).longValue();
	}

	private static BigDecimal toMoney(Object value) {
		if (value instanceof BigDecimal money) {
			return money;
		}
		return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
	}

	public record StatusMoney(long count, BigDecimal amount) {
	}
}
