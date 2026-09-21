package com.finance.platform.finance.application.service;

import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.finance.application.dto.ClientMonthlyEvidenceItemResponse;
import com.finance.platform.finance.application.dto.GenerateMonthlyEvidenceRequestsRequest;
import com.finance.platform.finance.application.dto.GenerateMonthlyEvidenceRequestsResponse;
import com.finance.platform.finance.application.dto.UpsertClientMonthlyEvidenceItemRequest;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.domain.model.ClientMonthlyEvidenceItem;
import com.finance.platform.finance.domain.model.DocumentRequest;
import com.finance.platform.finance.infrastructure.persistence.ClientMonthlyEvidenceJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.DocumentRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientMonthlyEvidenceService {

	private final ClientMonthlyEvidenceJpaRepository evidenceRepository;
	private final ClientAccessService clientAccessService;
	private final DocumentRequestService documentRequestService;
	private final PeriodCloseService periodCloseService;
	private final DocumentRequestJpaRepository documentRequestRepository;

	@Transactional(readOnly = true)
	public List<ClientMonthlyEvidenceItemResponse> list(UUID clientId) {
		clientAccessService.requireReadAccess(clientId);
		return evidenceRepository.findByClient_IdAndActiveTrueOrderBySortOrderAscTitleAsc(clientId)
				.stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public ClientMonthlyEvidenceItemResponse create(UUID clientId, UpsertClientMonthlyEvidenceItemRequest request) {
		Client client = clientAccessService.requireWriteAccess(clientId);
		ClientMonthlyEvidenceItem item = ClientMonthlyEvidenceItem.builder()
				.client(client)
				.title(request.title().trim())
				.description(trimOrNull(request.description()))
				.documentType(request.documentType() != null
						? request.documentType()
						: com.finance.platform.finance.domain.model.Receipt.DocumentType.OTHER)
				.required(request.required() == null || request.required())
				.responsibleParty(request.responsibleParty() != null
						? request.responsibleParty()
						: ClientMonthlyEvidenceItem.ResponsibleParty.CLIENT)
				.active(request.active() == null || request.active())
				.sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
				.build();
		item.setFirmId(client.getFirmId());
		return toResponse(evidenceRepository.save(item));
	}

	@Transactional
	public ClientMonthlyEvidenceItemResponse update(
			UUID clientId,
			UUID itemId,
			UpsertClientMonthlyEvidenceItemRequest request
	) {
		clientAccessService.requireWriteAccess(clientId);
		ClientMonthlyEvidenceItem item = evidenceRepository.findById(itemId)
				.filter(row -> row.getClient().getId().equals(clientId))
				.orElseThrow(() -> new ResourceNotFoundException("Monthly evidence item", itemId));
		item.setTitle(request.title().trim());
		item.setDescription(trimOrNull(request.description()));
		if (request.documentType() != null) {
			item.setDocumentType(request.documentType());
		}
		if (request.required() != null) {
			item.setRequired(request.required());
		}
		if (request.responsibleParty() != null) {
			item.setResponsibleParty(request.responsibleParty());
		}
		if (request.active() != null) {
			item.setActive(request.active());
		}
		if (request.sortOrder() != null) {
			item.setSortOrder(request.sortOrder());
		}
		return toResponse(evidenceRepository.save(item));
	}

	@Transactional
	public GenerateMonthlyEvidenceRequestsResponse generateRequests(
			UUID clientId,
			GenerateMonthlyEvidenceRequestsRequest request
	) {
		clientAccessService.requireWriteAccess(clientId);
		YearMonth ym = YearMonth.of(request.year(), request.month());
		String monthLabel = ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear();
		var period = periodCloseService.getOrCreate(clientId, request.year(), request.month());
		UUID periodId = period.id();

		List<ClientMonthlyEvidenceItem> items = evidenceRepository
				.findByClient_IdAndActiveTrueOrderBySortOrderAscTitleAsc(clientId);
		if (request.itemIds() != null && !request.itemIds().isEmpty()) {
			Set<UUID> wanted = Set.copyOf(request.itemIds());
			items = items.stream().filter(item -> wanted.contains(item.getId())).toList();
		}

		LocalDate dueDate = request.dueDate() != null ? request.dueDate() : ym.atEndOfMonth();
		List<DocumentRequest> existing = documentRequestRepository.findByClient_IdOrderByCreatedAtDesc(clientId);
		Set<String> openTitles = existing.stream()
				.filter(r -> r.getStatus() == DocumentRequest.RequestStatus.OPEN
						|| r.getStatus() == DocumentRequest.RequestStatus.UPLOADED)
				.map(r -> normalizeTitle(r.getTitle(), r.getDescription()))
				.collect(Collectors.toSet());

		List<UUID> createdIds = new ArrayList<>();
		List<String> skipped = new ArrayList<>();
		for (ClientMonthlyEvidenceItem item : items) {
			if (item.getResponsibleParty() != ClientMonthlyEvidenceItem.ResponsibleParty.CLIENT) {
				skipped.add(item.getTitle() + " (firm responsibility)");
				continue;
			}
			String requestTitle = monthLabel + " — " + item.getTitle();
			if (openTitles.contains(normalizeTitle(requestTitle, null))) {
				skipped.add(item.getTitle() + " (request already open)");
				continue;
			}
			String message = item.getDescription() != null && !item.getDescription().isBlank()
					? item.getDescription()
					: "Please upload " + item.getTitle().toLowerCase(Locale.ENGLISH) + " for " + monthLabel + ".";
			DocumentRequest saved = documentRequestService.create(
					clientId,
					requestTitle,
					message,
					item.getDocumentType(),
					dueDate,
					DocumentRequest.RequestPriority.NORMAL,
					null,
					periodId
			);
			createdIds.add(saved.getId());
			openTitles.add(normalizeTitle(requestTitle, null));
		}
		return new GenerateMonthlyEvidenceRequestsResponse(createdIds.size(), createdIds, skipped);
	}

	private static String normalizeTitle(String title, String description) {
		String base = title != null && !title.isBlank() ? title : description;
		return base == null ? "" : base.trim().toLowerCase(Locale.ROOT);
	}

	private static String trimOrNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private ClientMonthlyEvidenceItemResponse toResponse(ClientMonthlyEvidenceItem item) {
		return new ClientMonthlyEvidenceItemResponse(
				item.getId(),
				item.getTitle(),
				item.getDescription(),
				item.getDocumentType(),
				item.isRequired(),
				item.getResponsibleParty(),
				item.isActive(),
				item.getSortOrder()
		);
	}
}
