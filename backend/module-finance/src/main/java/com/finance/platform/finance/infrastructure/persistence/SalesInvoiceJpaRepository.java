package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.invoicing.SalesInvoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesInvoiceJpaRepository extends JpaRepository<SalesInvoice, UUID> {

	List<SalesInvoice> findByFirmIdOrderByCreatedAtDesc(UUID firmId);

	Optional<SalesInvoice> findByIdAndFirmId(UUID id, UUID firmId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from SalesInvoice i where i.id = :id and i.firmId = :firmId")
	Optional<SalesInvoice> findForUpdate(@Param("id") UUID id, @Param("firmId") UUID firmId);

	@Query(value = """
			select count(*) from sales_invoices
			where firm_id = :firmId and status = 'ISSUED'
			  and settlement_status in ('UNPAID','PARTIALLY_PAID','OVERDUE')
			  and due_date is not null and due_date < current_date
			""", nativeQuery = true)
	long countOverdue(@Param("firmId") UUID firmId);
}
