package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.invoicing.FirmInvoiceNumberSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FirmInvoiceNumberSequenceJpaRepository extends JpaRepository<FirmInvoiceNumberSequence, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from FirmInvoiceNumberSequence s where s.firmId = :firmId")
	Optional<FirmInvoiceNumberSequence> findForUpdate(@Param("firmId") UUID firmId);
}
