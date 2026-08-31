package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.AccountingPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingPeriodJpaRepository extends JpaRepository<AccountingPeriod, UUID> {

	Optional<AccountingPeriod> findByClient_IdAndPeriodYearAndPeriodMonth(UUID clientId, int year, int month);

	List<AccountingPeriod> findByClient_IdOrderByPeriodYearDescPeriodMonthDesc(UUID clientId);

	Optional<AccountingPeriod> findByIdAndClient_Id(UUID id, UUID clientId);

	@Query("""
			select p from AccountingPeriod p
			left join fetch p.client
			left join fetch p.closedBy
			left join fetch p.reviewStartedBy
			left join fetch p.reopenedBy
			where p.id = :id and p.client.id = :clientId
			""")
	Optional<AccountingPeriod> findDetailedByIdAndClient_Id(@Param("id") UUID id, @Param("clientId") UUID clientId);

	@Query("""
			select p from AccountingPeriod p
			left join fetch p.client
			left join fetch p.closedBy
			left join fetch p.reviewStartedBy
			left join fetch p.reopenedBy
			where p.client.id = :clientId
			order by p.periodYear desc, p.periodMonth desc
			""")
	List<AccountingPeriod> findDetailedByClient_Id(@Param("clientId") UUID clientId);

	@Query("""
			select p from AccountingPeriod p
			where p.firmId = :firmId
			  and p.client.id = :clientId
			  and p.status = :status
			  and p.startDate <= :date
			  and p.endDate >= :date
			""")
	Optional<AccountingPeriod> findClosedContaining(
			@Param("firmId") UUID firmId,
			@Param("clientId") UUID clientId,
			@Param("status") AccountingPeriod.PeriodStatus status,
			@Param("date") LocalDate date);

	@Query("""
			select count(p) > 0 from AccountingPeriod p
			where p.firmId = :firmId
			  and p.client.id = :clientId
			  and p.startDate <= :endDate
			  and p.endDate >= :startDate
			""")
	boolean existsOverlapping(
			@Param("firmId") UUID firmId,
			@Param("clientId") UUID clientId,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	@Query("""
			select count(p) > 0 from AccountingPeriod p
			where p.firmId = :firmId
			  and p.client.id = :clientId
			  and p.id <> :excludeId
			  and p.startDate <= :endDate
			  and p.endDate >= :startDate
			""")
	boolean existsOverlappingExcluding(
			@Param("firmId") UUID firmId,
			@Param("clientId") UUID clientId,
			@Param("excludeId") UUID excludeId,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);
}
