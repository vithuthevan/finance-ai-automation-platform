package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.invoicing.ArPayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArPaymentJpaRepository extends JpaRepository<ArPayment, UUID> {

	List<ArPayment> findByFirmIdOrderByPaymentDateDescCreatedAtDesc(UUID firmId);

	Optional<ArPayment> findByIdAndFirmId(UUID id, UUID firmId);

	Optional<ArPayment> findByFirmIdAndBankTransactionIdAndStatusNot(
			UUID firmId, UUID bankTransactionId, ArPayment.Status excludedStatus);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from ArPayment p where p.id = :id and p.firmId = :firmId")
	Optional<ArPayment> findForUpdate(@Param("id") UUID id, @Param("firmId") UUID firmId);
}
