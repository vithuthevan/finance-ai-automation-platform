package com.finance.platform.finance.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public class PracticeWorkQueryRepository {

	@PersistenceContext
	private EntityManager entityManager;

	public long countDocumentsNeedingReview(UUID firmId, Collection<UUID> clientIds, boolean allClients) {
		return scalarCount(
				"select count(*) from receipts where firm_id = :firmId and deleted_at is null "
						+ "and status in ('UPLOADED','NEEDS_REVIEW','EXTRACTED','PROCESSING','FAILED')"
						+ clientFilter("client_id", allClients),
				firmId, clientIds, allClients);
	}

	public long countProcessingFailures(UUID firmId, Collection<UUID> clientIds, boolean allClients) {
		return scalarCount(
				"select count(*) from receipts where firm_id = :firmId and deleted_at is null and status = 'FAILED'"
						+ clientFilter("client_id", allClients),
				firmId, clientIds, allClients);
	}

	public long countPendingApprovals(UUID firmId, Collection<UUID> clientIds, boolean allClients) {
		long expenses = scalarCount(
				"select count(*) from expenses where firm_id = :firmId and status = 'DRAFT'"
						+ clientFilter("client_id", allClients),
				firmId, clientIds, allClients);
		long income = scalarCount(
				"select count(*) from income where firm_id = :firmId and status = 'DRAFT'"
						+ clientFilter("client_id", allClients),
				firmId, clientIds, allClients);
		return expenses + income;
	}

	public long countOpenDocumentRequests(UUID firmId, Collection<UUID> clientIds, boolean allClients) {
		return scalarCount(
				"select count(*) from document_requests where firm_id = :firmId and status in ('OPEN','UPLOADED')"
						+ clientFilter("client_id", allClients),
				firmId, clientIds, allClients);
	}

	public long countOverdueDocumentRequests(UUID firmId, Collection<UUID> clientIds, boolean allClients) {
		return scalarCount(
				"select count(*) from document_requests where firm_id = :firmId and status in ('OPEN','UPLOADED') "
						+ "and due_date is not null and due_date < current_date"
						+ clientFilter("client_id", allClients),
				firmId, clientIds, allClients);
	}

	public long countBankUnresolved(UUID firmId, Collection<UUID> clientIds, boolean allClients) {
		return scalarCount(
				"select count(*) from bank_transactions bt "
						+ "join bank_accounts ba on ba.id = bt.bank_account_id "
						+ "where bt.firm_id = :firmId and bt.match_status in ('UNMATCHED','SUGGESTED','PENDING_APPROVAL')"
						+ clientFilter("ba.client_id", allClients),
				firmId, clientIds, allClients);
	}

	@SuppressWarnings("unchecked")
	public List<WorkRow> listDocumentReview(UUID firmId, Collection<UUID> clientIds, boolean allClients, int limit) {
		Query query = entityManager.createNativeQuery("""
				select r.id, r.client_id, c.name, coalesce(r.description, r.original_filename, 'Document'),
				       r.uploaded_at
				from receipts r
				join clients c on c.id = r.client_id
				where r.firm_id = :firmId and r.deleted_at is null
				  and r.status in ('UPLOADED','NEEDS_REVIEW','EXTRACTED','PROCESSING')
				""" + clientFilter("r.client_id", allClients) + """
				order by r.uploaded_at desc nulls last
				limit :limit
				""");
		bindClientScope(query, firmId, clientIds, allClients);
		query.setParameter("limit", limit);
		return mapWorkRows(query.getResultList(), "DOCUMENT_REVIEW", "NORMAL", "/app/documents/");
	}

	@SuppressWarnings("unchecked")
	public List<WorkRow> listProcessingFailures(UUID firmId, Collection<UUID> clientIds, boolean allClients, int limit) {
		Query query = entityManager.createNativeQuery("""
				select r.id, r.client_id, c.name, coalesce(r.description, r.original_filename, 'Failed document'),
				       r.uploaded_at
				from receipts r
				join clients c on c.id = r.client_id
				where r.firm_id = :firmId and r.deleted_at is null and r.status = 'FAILED'
				""" + clientFilter("r.client_id", allClients) + """
				order by r.uploaded_at desc nulls last
				limit :limit
				""");
		bindClientScope(query, firmId, clientIds, allClients);
		query.setParameter("limit", limit);
		return mapWorkRows(query.getResultList(), "DOCUMENT_PROCESSING_FAILURE", "HIGH", "/app/documents/");
	}

	@SuppressWarnings("unchecked")
	public List<WorkRow> listDraftApprovals(UUID firmId, Collection<UUID> clientIds, boolean allClients, int limit) {
		Query query = entityManager.createNativeQuery("""
				select x.id, x.client_id, c.name, x.title, x.created_at, x.kind
				from (
				  select e.id, e.client_id, coalesce(e.description, e.vendor_name, 'Draft expense') as title,
				         e.created_at, 'EXPENSE' as kind
				  from expenses e where e.firm_id = :firmId and e.status = 'DRAFT'
				  """ + clientFilter("e.client_id", allClients) + """
				  union all
				  select i.id, i.client_id, coalesce(i.description, i.customer_name, 'Draft income') as title,
				         i.created_at, 'INCOME' as kind
				  from income i where i.firm_id = :firmId and i.status = 'DRAFT'
				  """ + clientFilter("i.client_id", allClients) + """
				) x
				join clients c on c.id = x.client_id
				order by x.created_at desc
				limit :limit
				""");
		bindClientScope(query, firmId, clientIds, allClients);
		query.setParameter("limit", limit);
		List<Object[]> rows = query.getResultList();
		return rows.stream().map(row -> {
			UUID resourceId = (UUID) row[0];
			UUID clientId = (UUID) row[1];
			String clientName = (String) row[2];
			String title = (String) row[3];
			Instant createdAt = toInstant(row[4]);
			String kind = String.valueOf(row[5]);
			String path = "EXPENSE".equals(kind) ? "/app/expenses?clientId=" + clientId : "/app/income?clientId=" + clientId;
			return new WorkRow("TRANSACTION_APPROVAL", "NORMAL", clientId, clientName, title, title,
					resourceId, path, null, false, createdAt, null, null);
		}).toList();
	}

	@SuppressWarnings("unchecked")
	public List<WorkRow> listDocumentRequests(UUID firmId, Collection<UUID> clientIds, boolean allClients, int limit) {
		Query query = entityManager.createNativeQuery("""
				select dr.id, dr.client_id, c.name,
				       coalesce(dr.title, dr.description),
				       dr.description, dr.due_date, dr.created_at, dr.assignee_user_id
				from document_requests dr
				join clients c on c.id = dr.client_id
				where dr.firm_id = :firmId and dr.status in ('OPEN','UPLOADED')
				""" + clientFilter("dr.client_id", allClients) + """
				order by dr.due_date nulls last, dr.created_at desc
				limit :limit
				""");
		bindClientScope(query, firmId, clientIds, allClients);
		query.setParameter("limit", limit);
		List<Object[]> rows = query.getResultList();
		LocalDate today = LocalDate.now();
		return rows.stream().map(row -> {
			UUID resourceId = (UUID) row[0];
			UUID clientId = (UUID) row[1];
			String clientName = (String) row[2];
			String title = (String) row[3];
			String description = (String) row[4];
			LocalDate dueDate = row[5] == null ? null : ((java.sql.Date) row[5]).toLocalDate();
			boolean overdue = dueDate != null && dueDate.isBefore(today);
			String priority = overdue ? "HIGH" : "NORMAL";
			return new WorkRow("DOCUMENT_REQUEST", priority, clientId, clientName, title, description,
					resourceId, "/app/owner", dueDate, overdue, toInstant(row[6]), (UUID) row[7], null);
		}).toList();
	}

	@SuppressWarnings("unchecked")
	public List<WorkRow> listBankReconciliation(UUID firmId, Collection<UUID> clientIds, boolean allClients, int limit) {
		Query query = entityManager.createNativeQuery("""
				select bt.id, ba.client_id, c.name,
				       coalesce(bt.description, bt.counterparty, 'Bank transaction'),
				       bt.transaction_date
				from bank_transactions bt
				join bank_accounts ba on ba.id = bt.bank_account_id
				join clients c on c.id = ba.client_id
				where bt.firm_id = :firmId and bt.match_status in ('UNMATCHED','SUGGESTED','PENDING_APPROVAL')
				""" + clientFilter("ba.client_id", allClients) + """
				order by bt.transaction_date desc
				limit :limit
				""");
		bindClientScope(query, firmId, clientIds, allClients);
		query.setParameter("limit", limit);
		List<Object[]> rows = query.getResultList();
		return rows.stream().map(row -> new WorkRow(
				"BANK_RECONCILIATION",
				"NORMAL",
				(UUID) row[1],
				(String) row[2],
				(String) row[3],
				(String) row[3],
				(UUID) row[0],
				"/app/banking?clientId=" + row[1],
				null,
				false,
				toInstant(row[4]),
				null,
				null
		)).toList();
	}

	public long countDocumentsForAccountant(UUID firmId, UUID userId) {
		Query query = entityManager.createNativeQuery("""
				select count(*) from receipts r
				join clients c on c.id = r.client_id
				where r.firm_id = :firmId and r.deleted_at is null
				  and r.status in ('UPLOADED','NEEDS_REVIEW','EXTRACTED','PROCESSING','FAILED')
				  and (c.primary_accountant_user_id = :userId
				       or exists (
				         select 1 from user_client_access uca
				         join users u on u.id = uca.user_id
				         where uca.client_id = c.id and uca.user_id = :userId
				           and u.deleted_at is null
				       ))
				""");
		query.setParameter("firmId", firmId);
		query.setParameter("userId", userId);
		return ((Number) query.getSingleResult()).longValue();
	}

	private long scalarCount(String sql, UUID firmId, Collection<UUID> clientIds, boolean allClients) {
		Query query = entityManager.createNativeQuery(sql);
		bindClientScope(query, firmId, clientIds, allClients);
		return ((Number) query.getSingleResult()).longValue();
	}

	private static String clientFilter(String column, boolean allClients) {
		return allClients ? "" : " and " + column + " in (:clientIds)";
	}

	private static void bindClientScope(Query query, UUID firmId, Collection<UUID> clientIds, boolean allClients) {
		query.setParameter("firmId", firmId);
		if (!allClients) {
			query.setParameter("clientIds", clientIds);
		}
	}

	private static List<WorkRow> mapWorkRows(List<Object[]> rows, String type, String priority, String pathPrefix) {
		return rows.stream().map(row -> {
			UUID resourceId = (UUID) row[0];
			UUID clientId = (UUID) row[1];
			return new WorkRow(type, priority, clientId, (String) row[2], (String) row[3], (String) row[3],
					resourceId, pathPrefix + clientId + "/" + resourceId, null, false, toInstant(row[4]), null, null);
		}).toList();
	}

	private static Instant toInstant(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Instant instant) {
			return instant;
		}
		if (value instanceof java.sql.Timestamp timestamp) {
			return timestamp.toInstant();
		}
		if (value instanceof java.sql.Date date) {
			return date.toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
		}
		if (value instanceof LocalDate localDate) {
			return localDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
		}
		return null;
	}

	public record WorkRow(
			String type,
			String priority,
			UUID clientId,
			String clientName,
			String title,
			String description,
			UUID resourceId,
			String actionUrl,
			LocalDate dueDate,
			boolean overdue,
			Instant createdAt,
			UUID assignedUserId,
			String assignedUserName
	) {
	}
}
