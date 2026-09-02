package com.finance.platform.finance.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Repository
public class UsageQueryRepository {

	@PersistenceContext
	private EntityManager entityManager;

	public long countActiveClients(UUID firmId) {
		return scalar("select count(*) from clients where firm_id = :firmId and deleted_at is null and active = true", firmId);
	}

	public long countActiveUsers(UUID firmId) {
		return scalar("select count(*) from users where firm_id = :firmId and deleted_at is null and active = true", firmId);
	}

	public long countDocumentsInPeriod(UUID firmId, Instant from, Instant to) {
		Query query = entityManager.createNativeQuery("""
				select count(*) from receipts
				where firm_id = :firmId and deleted_at is null
				  and uploaded_at >= :from and uploaded_at < :to
				""");
		query.setParameter("firmId", firmId);
		query.setParameter("from", from);
		query.setParameter("to", to);
		return ((Number) query.getSingleResult()).longValue();
	}

	public long countAiProcessingInPeriod(UUID firmId, Instant from, Instant to) {
		Query query = entityManager.createNativeQuery("""
				select count(*) from document_processing_attempts dpa
				join receipts r on r.id = dpa.receipt_id
				where r.firm_id = :firmId
				  and dpa.status in ('SUCCESS', 'FAILED')
				  and dpa.created_at >= :from and dpa.created_at < :to
				""");
		query.setParameter("firmId", firmId);
		query.setParameter("from", from);
		query.setParameter("to", to);
		return ((Number) query.getSingleResult()).longValue();
	}

	public long sumStorageBytes(UUID firmId) {
		Query query = entityManager.createNativeQuery("""
				select coalesce(sum(file_size_bytes), 0) from receipts
				where firm_id = :firmId and deleted_at is null
				""");
		query.setParameter("firmId", firmId);
		return ((Number) query.getSingleResult()).longValue();
	}

	public long countFirmsByStatus(String status) {
		Query query = entityManager.createNativeQuery(
				"select count(*) from firm_subscriptions where status = :status");
		query.setParameter("status", status);
		return ((Number) query.getSingleResult()).longValue();
	}

	public long countAllFirms() {
		return ((Number) entityManager.createNativeQuery("select count(*) from firms where active = true")
				.getSingleResult()).longValue();
	}

	private long scalar(String sql, UUID firmId) {
		Query query = entityManager.createNativeQuery(sql);
		query.setParameter("firmId", firmId);
		return ((Number) query.getSingleResult()).longValue();
	}
}
