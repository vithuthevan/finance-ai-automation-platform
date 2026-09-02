package com.finance.platform.reporting.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.ByteBuffer;

@Repository
@RequiredArgsConstructor
public class ReportingQueryRepository {

	private final EntityManager entityManager;

	public BigDecimal sumApproved(UUID firmId, UUID clientId, String transactionType, LocalDate from, LocalDate to) {
		String table = tableName(transactionType);
		Query query = entityManager.createNativeQuery(
				"select coalesce(sum(amount), 0) from " + table
						+ " where firm_id = :firmId and client_id = :clientId and status = 'APPROVED'"
						+ " and transaction_date between :from and :to");
		bindScope(query, firmId, clientId, from, to);
		return toMoney(query.getSingleResult());
	}

	public long countApproved(UUID firmId, UUID clientId, String transactionType, LocalDate from, LocalDate to) {
		String table = tableName(transactionType);
		Query query = entityManager.createNativeQuery(
				"select count(*) from " + table
						+ " where firm_id = :firmId and client_id = :clientId and status = 'APPROVED'"
						+ " and transaction_date between :from and :to");
		bindScope(query, firmId, clientId, from, to);
		return ((Number) query.getSingleResult()).longValue();
	}

	public List<CategoryTotal> categoryTotals(UUID firmId, UUID clientId, String transactionType, LocalDate from, LocalDate to) {
		String table = tableName(transactionType);
		Query query = entityManager.createNativeQuery(
				"select c.id, c.code, c.name, c.parent_id, p.name as parent_name, coalesce(sum(t.amount), 0) "
						+ "from " + table + " t "
						+ "join categories c on c.id = t.category_id "
						+ "left join categories p on p.id = c.parent_id "
						+ "where t.firm_id = :firmId and t.client_id = :clientId and t.status = 'APPROVED' "
						+ "and t.transaction_date between :from and :to "
						+ "group by c.id, c.code, c.name, c.parent_id, p.name "
						+ "order by 6 desc");
		bindScope(query, firmId, clientId, from, to);
		List<CategoryTotal> rows = new ArrayList<>();
		for (Object raw : query.getResultList()) {
			Object[] cols = (Object[]) raw;
			rows.add(new CategoryTotal(
					toUuid(cols[0]),
					(String) cols[1],
					(String) cols[2],
					toUuid(cols[3]),
					(String) cols[4],
					toMoney(cols[5])));
		}
		return rows;
	}

	public List<NamedTotal> incomeByPaymentMethod(UUID firmId, UUID clientId, LocalDate from, LocalDate to) {
		Query query = entityManager.createNativeQuery(
				"select coalesce(payment_method, 'UNSPECIFIED'), coalesce(sum(amount), 0), count(*) "
						+ "from income where firm_id = :firmId and client_id = :clientId and status = 'APPROVED' "
						+ "and transaction_date between :from and :to "
						+ "group by coalesce(payment_method, 'UNSPECIFIED') order by 2 desc");
		bindScope(query, firmId, clientId, from, to);
		List<NamedTotal> rows = new ArrayList<>();
		for (Object raw : query.getResultList()) {
			Object[] cols = (Object[]) raw;
			rows.add(new NamedTotal((String) cols[0], toMoney(cols[1]), ((Number) cols[2]).longValue()));
		}
		return rows;
	}

	public List<MonthlyTotal> monthlyTotals(UUID firmId, UUID clientId, String transactionType, LocalDate from, LocalDate to) {
		String table = tableName(transactionType);
		Query query = entityManager.createNativeQuery(
				"select cast(date_trunc('month', transaction_date) as date), coalesce(sum(amount), 0) "
						+ "from " + table
						+ " where firm_id = :firmId and client_id = :clientId and status = 'APPROVED' "
						+ "and transaction_date between :from and :to "
						+ "group by 1 order by 1");
		bindScope(query, firmId, clientId, from, to);
		List<MonthlyTotal> rows = new ArrayList<>();
		for (Object raw : query.getResultList()) {
			Object[] cols = (Object[]) raw;
			rows.add(new MonthlyTotal(toLocalDate(cols[0]), toMoney(cols[1])));
		}
		return rows;
	}

	public List<LargestTransaction> largestApproved(UUID firmId, UUID clientId, String transactionType, LocalDate from, LocalDate to, int limit) {
		String table = tableName(transactionType);
		String party = "INCOME".equals(transactionType) ? "customer_name" : "vendor_name";
		Query query = entityManager.createNativeQuery(
				"select t.id, t.transaction_date, t.amount, t." + party + ", c.name "
						+ "from " + table + " t left join categories c on c.id = t.category_id "
						+ "where t.firm_id = :firmId and t.client_id = :clientId and t.status = 'APPROVED' "
						+ "and t.transaction_date between :from and :to "
						+ "order by t.amount desc");
		bindScope(query, firmId, clientId, from, to);
		query.setMaxResults(Math.max(1, limit));
		List<LargestTransaction> rows = new ArrayList<>();
		for (Object raw : query.getResultList()) {
			Object[] cols = (Object[]) raw;
			rows.add(new LargestTransaction(
					toUuid(cols[0]),
					toLocalDate(cols[1]),
					toMoney(cols[2]),
					(String) cols[3],
					(String) cols[4]));
		}
		return rows;
	}

	public Map<String, Long> statusCounts(UUID firmId, UUID clientId, String transactionType) {
		String table = tableName(transactionType);
		Query query = entityManager.createNativeQuery(
				"select status, count(*) from " + table
						+ " where firm_id = :firmId and client_id = :clientId group by status");
		query.setParameter("firmId", firmId);
		query.setParameter("clientId", clientId);
		Map<String, Long> counts = new LinkedHashMap<>();
		for (Object raw : query.getResultList()) {
			Object[] cols = (Object[]) raw;
			counts.put((String) cols[0], ((Number) cols[1]).longValue());
		}
		return counts;
	}

	public DocumentSupportCounts documentSupport(UUID firmId, UUID clientId, LocalDate from, LocalDate to) {
		long withDocs = countApprovedWithDocument(firmId, clientId, "expenses", "expense_receipts", "expense_id", from, to)
				+ countApprovedWithDocument(firmId, clientId, "income", "income_receipts", "income_id", from, to);
		long approved = countApproved(firmId, clientId, "EXPENSE", from, to)
				+ countApproved(firmId, clientId, "INCOME", from, to);
		Query unlinked = entityManager.createNativeQuery(
				"select count(*) from receipts where firm_id = :firmId and client_id = :clientId "
						+ "and deleted_at is null and status not in ('LINKED', 'REJECTED')");
		unlinked.setParameter("firmId", firmId);
		unlinked.setParameter("clientId", clientId);
		Query review = entityManager.createNativeQuery(
				"select count(*) from receipts where firm_id = :firmId and client_id = :clientId "
						+ "and deleted_at is null and status in ('UPLOADED', 'NEEDS_REVIEW', 'EXTRACTED', 'PROCESSING')");
		review.setParameter("firmId", firmId);
		review.setParameter("clientId", clientId);
		return new DocumentSupportCounts(
				withDocs,
				Math.max(0, approved - withDocs),
				((Number) unlinked.getSingleResult()).longValue(),
				((Number) review.getSingleResult()).longValue());
	}

	public PracticeCounts practiceCounts(UUID firmId, Collection<UUID> clientIds, boolean allClients) {
		if (!allClients && (clientIds == null || clientIds.isEmpty())) {
			return new PracticeCounts(0, 0, 0, 0, 0);
		}
		String clientFilter = allClients ? "" : " and id in (:clientIds)";
		Query active = entityManager.createNativeQuery(
				"select count(*) from clients where firm_id = :firmId and deleted_at is null and active = true" + clientFilter);
		active.setParameter("firmId", firmId);
		if (!allClients) {
			active.setParameter("clientIds", clientIds);
		}
		String scoped = allClients ? "" : " and client_id in (:clientIds)";
		Query drafts = entityManager.createNativeQuery(
				"select count(*) from ("
						+ "select client_id from expenses where firm_id = :firmId and status = 'DRAFT'" + scoped
						+ " union "
						+ "select client_id from income where firm_id = :firmId and status = 'DRAFT'" + scoped
						+ ") t");
		drafts.setParameter("firmId", firmId);
		if (!allClients) {
			drafts.setParameter("clientIds", clientIds);
		}
		Query pending = entityManager.createNativeQuery(
				"select ("
						+ "select count(*) from expenses where firm_id = :firmId and status = 'DRAFT'" + scoped
						+ ") + ("
						+ "select count(*) from income where firm_id = :firmId and status = 'DRAFT'" + scoped
						+ ")");
		pending.setParameter("firmId", firmId);
		if (!allClients) {
			pending.setParameter("clientIds", clientIds);
		}
		Query review = entityManager.createNativeQuery(
				"select count(*) from receipts where firm_id = :firmId and deleted_at is null "
						+ "and status in ('UPLOADED', 'NEEDS_REVIEW', 'EXTRACTED', 'PROCESSING')"
						+ (allClients ? "" : " and client_id in (:clientIds)"));
		review.setParameter("firmId", firmId);
		if (!allClients) {
			review.setParameter("clientIds", clientIds);
		}
		Query recent = entityManager.createNativeQuery(
				"select count(distinct client_id) from ("
						+ "select client_id from expenses where firm_id = :firmId and approved_at >= now() - interval '30 days'" + scoped
						+ " union "
						+ "select client_id from income where firm_id = :firmId and approved_at >= now() - interval '30 days'" + scoped
						+ " union "
						+ "select client_id from receipts where firm_id = :firmId and deleted_at is null "
						+ "and uploaded_at >= now() - interval '30 days'"
						+ (allClients ? "" : " and client_id in (:clientIds)")
						+ ") t");
		recent.setParameter("firmId", firmId);
		if (!allClients) {
			recent.setParameter("clientIds", clientIds);
		}
		return new PracticeCounts(
				((Number) active.getSingleResult()).longValue(),
				((Number) drafts.getSingleResult()).longValue(),
				((Number) review.getSingleResult()).longValue(),
				((Number) pending.getSingleResult()).longValue(),
				((Number) recent.getSingleResult()).longValue());
	}

	private long countApprovedWithDocument(
			UUID firmId,
			UUID clientId,
			String table,
			String joinTable,
			String joinColumn,
			LocalDate from,
			LocalDate to
	) {
		Query query = entityManager.createNativeQuery(
				"select count(*) from " + table + " t "
						+ "where t.firm_id = :firmId and t.client_id = :clientId and t.status = 'APPROVED' "
						+ "and t.transaction_date between :from and :to "
						+ "and (t.primary_receipt_id is not null or exists ("
						+ "select 1 from " + joinTable + " j where j." + joinColumn + " = t.id))");
		bindScope(query, firmId, clientId, from, to);
		return ((Number) query.getSingleResult()).longValue();
	}

	private static void bindScope(Query query, UUID firmId, UUID clientId, LocalDate from, LocalDate to) {
		query.setParameter("firmId", firmId);
		query.setParameter("clientId", clientId);
		query.setParameter("from", from);
		query.setParameter("to", to);
	}

	private static String tableName(String transactionType) {
		return "INCOME".equalsIgnoreCase(transactionType) ? "income" : "expenses";
	}

	private static BigDecimal toMoney(Object value) {
		if (value == null) {
			return BigDecimal.ZERO;
		}
		if (value instanceof BigDecimal decimal) {
			return decimal;
		}
		return new BigDecimal(value.toString());
	}

	private static UUID toUuid(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof UUID uuid) {
			return uuid;
		}
		if (value instanceof byte[] bytes && bytes.length == 16) {
			ByteBuffer buffer = ByteBuffer.wrap(bytes);
			return new UUID(buffer.getLong(), buffer.getLong());
		}
		return UUID.fromString(value.toString());
	}

	private static LocalDate toLocalDate(Object value) {
		if (value instanceof Date date) {
			return date.toLocalDate();
		}
		if (value instanceof LocalDate localDate) {
			return localDate;
		}
		return LocalDate.parse(value.toString().substring(0, 10));
	}

	public record CategoryTotal(UUID categoryId, String categoryCode, String categoryName, UUID parentId, String parentName, BigDecimal amount) {
	}

	public record NamedTotal(String name, BigDecimal amount, long count) {
	}

	public record MonthlyTotal(LocalDate monthStart, BigDecimal amount) {
	}

	public record LargestTransaction(UUID id, LocalDate transactionDate, BigDecimal amount, String partyName, String categoryName) {
	}

	public record DocumentSupportCounts(long approvedWithDocuments, long approvedWithoutDocuments, long unlinkedDocuments, long awaitingReview) {
	}

	public record PracticeCounts(long activeClients, long clientsWithDrafts, long documentsNeedingReview, long pendingApprovals, long clientsWithRecentActivity) {
	}
}
