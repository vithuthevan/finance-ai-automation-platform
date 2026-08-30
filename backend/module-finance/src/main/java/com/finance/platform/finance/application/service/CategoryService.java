package com.finance.platform.finance.application.service;

import com.finance.platform.auth.api.UserFacade;
import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.infrastructure.security.SecurityUser;
import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.DuplicateResourceException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.finance.application.dto.CategoryResponse;
import com.finance.platform.finance.application.dto.CreateCategoryRequest;
import com.finance.platform.finance.application.dto.UpdateCategoryRequest;
import com.finance.platform.finance.domain.model.Category;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.infrastructure.persistence.CategoryJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ClientJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

	private final CategoryJpaRepository categoryRepository;
	private final ClientJpaRepository clientRepository;
	private final ClientAccessService clientAccessService;
	private final UserFacade userFacade;
	private final AuditLogger auditLogger;

	@Transactional(readOnly = true)
	public List<CategoryResponse> listFirmCategories(UUID clientId, Category.CategoryType categoryType, Boolean active) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		if (clientId != null) {
			clientAccessService.requireReadAccess(clientId);
		}
		Set<UUID> assignedClients = currentUser.getRole() == Role.RoleCode.ADMIN
				? Set.of()
				: userFacade.getAccessibleClientIds(currentUser.getId());
		return categoryRepository.findAllByFirmIdAndDeletedAtIsNull(currentUser.getFirmId()).stream()
				.filter(category -> matchesClientScope(category, clientId))
				.filter(category -> visibleToCaller(category, currentUser, assignedClients))
				.filter(category -> categoryType == null || category.getCategoryType() == categoryType
						|| category.getCategoryType() == Category.CategoryType.BOTH)
				.filter(category -> active == null || category.isActive() == active)
				.map(this::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public CategoryResponse get(UUID categoryId) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		Category category = findFirmCategory(categoryId, currentUser.getFirmId());
		if (category.getClient() != null) {
			clientAccessService.requireReadAccess(category.getClient().getId());
		}
		return toResponse(category);
	}

	@Transactional
	public CategoryResponse create(CreateCategoryRequest request) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		assertAdmin(currentUser);

		String code = normalizeCode(request.code());
		String name = requireName(request.name());
		UUID firmId = currentUser.getFirmId();

		Client client = resolveClient(request.clientId(), firmId);
		assertCodeAvailable(firmId, client, code, null);
		Category parent = resolveParent(request.parentId(), firmId, client);

		Category category = Category.builder()
				.client(client)
				.parent(parent)
				.code(code)
				.name(name)
				.categoryType(request.categoryType())
				.system(false)
				.active(true)
				.build();
		category.setFirmId(firmId);

		Category saved = categoryRepository.save(category);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.CATEGORY_CREATED)
				.resourceType(AuditResourceType.CATEGORY)
				.resourceId(saved.getId())
				.clientId(client != null ? client.getId() : null)
				.afterState(categorySnapshot(saved))
				.build());
		return toResponse(saved);
	}

	@Transactional
	public CategoryResponse update(UUID categoryId, UpdateCategoryRequest request) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		assertAdmin(currentUser);
		Category category = findFirmCategory(categoryId, currentUser.getFirmId());
		if (category.isSystem()) {
			throw new BusinessException("System categories cannot be modified");
		}
		Map<String, Object> before = categorySnapshot(category);

		String code = normalizeCode(request.code());
		String name = requireName(request.name());
		assertCodeAvailable(currentUser.getFirmId(), category.getClient(), code, category.getId());
		Category parent = resolveParent(request.parentId(), currentUser.getFirmId(), category.getClient());
		assertNoCycle(category, parent);

		category.setCode(code);
		category.setName(name);
		category.setCategoryType(request.categoryType());
		category.setParent(parent);

		Category saved = categoryRepository.save(category);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.CATEGORY_UPDATED)
				.resourceType(AuditResourceType.CATEGORY)
				.resourceId(saved.getId())
				.clientId(saved.getClient() != null ? saved.getClient().getId() : null)
				.beforeState(before)
				.afterState(categorySnapshot(saved))
				.build());
		return toResponse(saved);
	}

	@Transactional
	public CategoryResponse setActive(UUID categoryId, boolean active) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		assertAdmin(currentUser);
		Category category = findFirmCategory(categoryId, currentUser.getFirmId());
		Map<String, Object> before = categorySnapshot(category);
		category.setActive(active);
		Category saved = categoryRepository.save(category);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(active ? AuditAction.CATEGORY_ACTIVATED : AuditAction.CATEGORY_DEACTIVATED)
				.resourceType(AuditResourceType.CATEGORY)
				.resourceId(saved.getId())
				.clientId(saved.getClient() != null ? saved.getClient().getId() : null)
				.beforeState(before)
				.afterState(categorySnapshot(saved))
				.build());
		return toResponse(saved);
	}

	private boolean matchesClientScope(Category category, UUID clientId) {
		if (clientId == null) {
			return true;
		}
		UUID categoryClientId = category.getClient() != null ? category.getClient().getId() : null;
		return categoryClientId == null || clientId.equals(categoryClientId);
	}

	private Client resolveClient(UUID clientId, UUID firmId) {
		if (clientId == null) {
			return null;
		}
		Client client = clientRepository.findByIdAndFirmIdAndDeletedAtIsNull(clientId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("Client", clientId));
		if (!client.isActive()) {
			throw new BusinessException(ErrorCodes.CLIENT_INACTIVE, "Client is inactive");
		}
		return client;
	}

	private Category resolveParent(UUID parentId, UUID firmId, Client client) {
		if (parentId == null) {
			return null;
		}
		Category parent = findFirmCategory(parentId, firmId);
		UUID parentClientId = parent.getClient() != null ? parent.getClient().getId() : null;
		UUID childClientId = client != null ? client.getId() : null;
		if (parentClientId != null && !parentClientId.equals(childClientId)) {
			throw new ValidationException("parentId", "Parent category is not in the same client scope");
		}
		return parent;
	}

	private void assertNoCycle(Category category, Category parent) {
		Category cursor = parent;
		while (cursor != null) {
			if (cursor.getId().equals(category.getId())) {
				throw new ValidationException("parentId", "Category cannot be its own ancestor");
			}
			cursor = cursor.getParent();
		}
	}

	private void assertCodeAvailable(UUID firmId, Client client, String code, UUID excludeId) {
		boolean exists;
		if (client == null) {
			exists = excludeId == null
					? categoryRepository.existsByFirmIdAndCodeAndClientIsNullAndDeletedAtIsNull(firmId, code)
					: categoryRepository.existsByFirmIdAndCodeAndClientIsNullAndDeletedAtIsNullAndIdNot(
					firmId, code, excludeId);
		} else {
			exists = excludeId == null
					? categoryRepository.existsByFirmIdAndClient_IdAndCodeAndDeletedAtIsNull(firmId, client.getId(), code)
					: categoryRepository.existsByFirmIdAndClient_IdAndCodeAndDeletedAtIsNullAndIdNot(
					firmId, client.getId(), code, excludeId);
		}
		if (exists) {
			throw new DuplicateResourceException(ErrorCodes.DUPLICATE_CATEGORY_CODE, "Category", "code", code);
		}
	}

	private boolean visibleToCaller(Category category, SecurityUser currentUser, Set<UUID> assignedClients) {
		if (currentUser.getRole() == Role.RoleCode.ADMIN || category.getClient() == null) {
			return true;
		}
		return assignedClients.contains(category.getClient().getId());
	}

	private Category findFirmCategory(UUID categoryId, UUID firmId) {
		return categoryRepository.findByIdAndFirmIdAndDeletedAtIsNull(categoryId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
	}

	private void assertAdmin(SecurityUser currentUser) {
		if (currentUser.getRole() != Role.RoleCode.ADMIN) {
			throw new BusinessException(ErrorCodes.ACCESS_DENIED, "Only administrators can manage categories");
		}
	}

	private CategoryResponse toResponse(Category category) {
		return new CategoryResponse(
				category.getId(),
				category.getFirmId(),
				category.getClient() != null ? category.getClient().getId() : null,
				category.getParent() != null ? category.getParent().getId() : null,
				category.getCode(),
				category.getName(),
				category.getCategoryType(),
				category.isSystem(),
				category.isActive()
		);
	}

	private static Map<String, Object> categorySnapshot(Category category) {
		Map<String, Object> state = new LinkedHashMap<>();
		state.put("code", category.getCode());
		state.put("name", category.getName());
		state.put("categoryType", category.getCategoryType() != null ? category.getCategoryType().name() : null);
		state.put("clientId", category.getClient() != null ? category.getClient().getId() : null);
		state.put("parentId", category.getParent() != null ? category.getParent().getId() : null);
		state.put("active", category.isActive());
		return state;
	}

	private static String normalizeCode(String rawCode) {
		String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
		if (code.isBlank()) {
			throw new ValidationException("code", "Category code is required");
		}
		return code;
	}

	private static String requireName(String raw) {
		String name = raw == null ? "" : raw.trim();
		if (name.isBlank()) {
			throw new ValidationException("name", "Category name is required");
		}
		return name;
	}
}
