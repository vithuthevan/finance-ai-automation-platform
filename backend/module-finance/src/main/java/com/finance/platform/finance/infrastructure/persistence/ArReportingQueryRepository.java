package com.finance.platform.finance.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public class ArReportingQueryRepository {

	@PersistenceContext
	private EntityManager entityManager;

	public BigDecimal sumOutstanding(UUID firmId) {
		return singleDecimal("""
				select coalesce(sum(i.total - coalesce(alloc.allocated, 0)), 0)
				from sales_invoices i
				left join (
				    select invoice_id, sum(amount) as allocated
				    from ar_payment_allocations
				    where active = true
				    group by invoice_id
				) alloc on alloc.invoice_id = i.id
				where i.firm_id = :firmId
				  and i.status = 'ISSUED'
				  and i.total > coalesce(alloc.allocated, 0)
				""", firmId);
	}

	public ArAgeingRow ageing(UUID firmId) {
		LocalDate today = LocalDate.now();
		Object[] row = (Object[]) entityManager.createNativeQuery("""
				select
				  coalesce(sum(case when i.due_date >= :today then outstanding end), 0),
				  coalesce(sum(case when i.due_date < :today and i.due_date >= :d30 then outstanding end), 0),
				  coalesce(sum(case when i.due_date < :d30 and i.due_date >= :d60 then outstanding end), 0),
				  coalesce(sum(case when i.due_date < :d60 and i.due_date >= :d90 then outstanding end), 0),
				  coalesce(sum(case when i.due_date < :d90 then outstanding end), 0)
				from (
				  select i.due_date,
				         (i.total - coalesce(alloc.allocated, 0)) as outstanding
				  from sales_invoices i
				  left join (
				      select invoice_id, sum(amount) as allocated
				      from ar_payment_allocations where active = true group by invoice_id
				  ) alloc on alloc.invoice_id = i.id
				  where i.firm_id = :firmId and i.status = 'ISSUED'
				    and i.total > coalesce(alloc.allocated, 0)
				) i
				""")
				.setParameter("firmId", firmId)
				.setParameter("today", today)
				.setParameter("d30", today.minusDays(30))
				.setParameter("d60", today.minusDays(60))
				.setParameter("d90", today.minusDays(90))
				.getSingleResult();
		return new ArAgeingRow(
				toDecimal(row[0]),
				toDecimal(row[1]),
				toDecimal(row[2]),
				toDecimal(row[3]),
				toDecimal(row[4])
		);
	}

	public List<CustomerBalanceRow> topOverdueCustomers(UUID firmId, int limit) {
		return entityManager.createNativeQuery("""
				select c.id, c.name,
				       sum(i.total - coalesce(alloc.allocated, 0)) as outstanding,
				       sum(case when i.due_date < current_date then i.total - coalesce(alloc.allocated, 0) else 0 end) as overdue
				from sales_invoices i
				join ar_customers c on c.id = i.customer_id
				left join (
				    select invoice_id, sum(amount) as allocated
				    from ar_payment_allocations where active = true group by invoice_id
				) alloc on alloc.invoice_id = i.id
				where i.firm_id = :firmId and i.status = 'ISSUED'
				  and i.total > coalesce(alloc.allocated, 0)
				  and i.due_date < current_date
				group by c.id, c.name
				order by overdue desc
				limit :limit
				""")
				.setParameter("firmId", firmId)
				.setParameter("limit", limit)
				.getResultList()
				.stream()
				.map(r -> {
					Object[] cols = (Object[]) r;
					return new CustomerBalanceRow((UUID) cols[0], (String) cols[1], toDecimal(cols[2]), toDecimal(cols[3]));
				})
				.toList();
	}

	public BigDecimal collectedThisMonth(UUID firmId) {
		return singleDecimal("""
				select coalesce(sum(a.amount), 0)
				from ar_payment_allocations a
				join ar_payments p on p.id = a.payment_id
				where p.firm_id = :firmId
				  and a.active = true
				  and p.status <> 'REVERSED'
				  and p.payment_date >= date_trunc('month', current_date)
				""", firmId);
	}

	public BigDecimal dueThisWeek(UUID firmId) {
		return singleDecimal("""
				select coalesce(sum(i.total - coalesce(alloc.allocated, 0)), 0)
				from sales_invoices i
				left join (
				    select invoice_id, sum(amount) as allocated
				    from ar_payment_allocations where active = true group by invoice_id
				) alloc on alloc.invoice_id = i.id
				where i.firm_id = :firmId and i.status = 'ISSUED'
				  and i.due_date between current_date and current_date + interval '7 days'
				  and i.total > coalesce(alloc.allocated, 0)
				""", firmId);
	}

	private BigDecimal singleDecimal(String sql, UUID firmId) {
		Object result = entityManager.createNativeQuery(sql)
				.setParameter("firmId", firmId)
				.getSingleResult();
		return toDecimal(result);
	}

	private static BigDecimal toDecimal(Object value) {
		if (value == null) {
			return BigDecimal.ZERO;
		}
		if (value instanceof BigDecimal decimal) {
			return decimal;
		}
		return new BigDecimal(value.toString());
	}

	public record ArAgeingRow(
			BigDecimal current,
			BigDecimal days1To30,
			BigDecimal days31To60,
			BigDecimal days61To90,
			BigDecimal days90Plus
	) {
	}

	public record CustomerBalanceRow(UUID customerId, String name, BigDecimal outstanding, BigDecimal overdue) {
	}
}
