package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.invoicing.ArPaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ArPaymentAllocationJpaRepository extends JpaRepository<ArPaymentAllocation, UUID> {

	List<ArPaymentAllocation> findByPayment_Id(UUID paymentId);

	@Query("""
			select coalesce(sum(a.amount), 0) from ArPaymentAllocation a
			where a.invoiceId = :invoiceId and a.active = true
			""")
	BigDecimal sumActiveAllocatedToInvoice(@Param("invoiceId") UUID invoiceId);

	@Query("""
			select coalesce(sum(a.amount), 0) from ArPaymentAllocation a
			where a.payment.id = :paymentId and a.active = true
			""")
	BigDecimal sumActiveAllocatedOnPayment(@Param("paymentId") UUID paymentId);
}
