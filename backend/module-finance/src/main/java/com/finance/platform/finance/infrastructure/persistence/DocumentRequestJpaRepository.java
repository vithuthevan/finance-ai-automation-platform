package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.DocumentRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRequestJpaRepository extends JpaRepository<DocumentRequest, UUID> {

	List<DocumentRequest> findByClient_IdOrderByCreatedAtDesc(UUID clientId);

	List<DocumentRequest> findByAssigneeUserIdAndStatus(UUID assigneeUserId, DocumentRequest.RequestStatus status);

	Optional<DocumentRequest> findByIdAndClient_Id(UUID id, UUID clientId);

	Optional<DocumentRequest> findByIdAndClient_IdAndFirmId(UUID id, UUID clientId, UUID firmId);

	long countByClient_IdAndStatus(UUID clientId, DocumentRequest.RequestStatus status);

	@Query("""
			select r from DocumentRequest r
			where r.client.id = :clientId
			  and (:status is null or r.status = :status)
			  and (:periodId is null or r.period.id = :periodId or r.period is null)
			""")
	Page<DocumentRequest> search(
			@Param("clientId") UUID clientId,
			@Param("status") DocumentRequest.RequestStatus status,
			@Param("periodId") UUID periodId,
			Pageable pageable);

	@Query("""
			select r from DocumentRequest r
			join fetch r.client
			where r.firmId = :firmId
			  and (:status is null or r.status = :status)
			""")
	Page<DocumentRequest> searchByFirm(
			@Param("firmId") UUID firmId,
			@Param("status") DocumentRequest.RequestStatus status,
			Pageable pageable);

	@Query("""
			select r from DocumentRequest r
			join fetch r.client
			where r.status in (com.finance.platform.finance.domain.model.DocumentRequest.RequestStatus.OPEN, com.finance.platform.finance.domain.model.DocumentRequest.RequestStatus.UPLOADED)
			  and r.dueDate is not null
			  and r.dueDate < :today
			""")
	List<DocumentRequest> findOverdueOpen(@Param("today") LocalDate today);

	@Query("""
			select r from DocumentRequest r
			join fetch r.client
			where r.firmId = :firmId
			  and r.status in (com.finance.platform.finance.domain.model.DocumentRequest.RequestStatus.OPEN,
			                   com.finance.platform.finance.domain.model.DocumentRequest.RequestStatus.UPLOADED)
			""")
	List<DocumentRequest> findOpenByFirmId(@Param("firmId") UUID firmId);
}
