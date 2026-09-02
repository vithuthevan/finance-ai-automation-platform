package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankAccountJpaRepository extends JpaRepository<BankAccount, UUID> {

	List<BankAccount> findByClient_IdAndActiveTrueOrderByBankNameAscAccountNameAsc(UUID clientId);

	List<BankAccount> findByClient_IdOrderByBankNameAscAccountNameAsc(UUID clientId);

	Optional<BankAccount> findByIdAndClient_Id(UUID id, UUID clientId);

	long countByClient_IdAndActiveTrue(UUID clientId);
}
