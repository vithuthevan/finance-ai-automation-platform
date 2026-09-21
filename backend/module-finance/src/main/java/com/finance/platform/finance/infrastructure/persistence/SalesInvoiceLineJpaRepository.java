package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.invoicing.SalesInvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SalesInvoiceLineJpaRepository extends JpaRepository<SalesInvoiceLine, UUID> {

	List<SalesInvoiceLine> findByInvoice_IdOrderByLineNoAsc(UUID invoiceId);

	void deleteByInvoice_Id(UUID invoiceId);
}
