package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.recon.ReconciliationMatchGroupItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReconciliationMatchGroupItemJpaRepository extends JpaRepository<ReconciliationMatchGroupItem, UUID> {

	boolean existsByBankTransaction_IdAndBankClaimActiveTrue(UUID bankTransactionId);

	@Query("""
			select i from ReconciliationMatchGroupItem i
			join i.group g
			where i.bankTransaction.id = :bankTransactionId
			  and i.bankClaimActive = true
			  and g.firmId = :firmId
			  and g.status = com.finance.platform.finance.domain.model.recon.ReconciliationMatchGroup.Status.CONFIRMED
			""")
	Optional<ReconciliationMatchGroupItem> findActiveBankClaim(
			@Param("bankTransactionId") UUID bankTransactionId,
			@Param("firmId") UUID firmId);
}
