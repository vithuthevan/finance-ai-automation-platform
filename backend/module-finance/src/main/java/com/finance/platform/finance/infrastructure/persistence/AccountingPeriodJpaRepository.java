package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.AccountingPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingPeriodJpaRepository extends JpaRepository<AccountingPeriod, UUID> {

	Optional<AccountingPeriod> findByClient_IdAndPeriodYearAndPeriodMonth(UUID clientId, int year, int month);

	List<AccountingPeriod> findByClient_IdOrderByPeriodYearDescPeriodMonthDesc(UUID clientId);

	Optional<AccountingPeriod> findByIdAndClient_Id(UUID id, UUID clientId);
}
