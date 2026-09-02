package com.finance.platform.controller.finance;

import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.finance.domain.model.Firm;
import com.finance.platform.finance.domain.model.FirmSubscription;
import com.finance.platform.finance.infrastructure.persistence.FirmJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.FirmSubscriptionJpaRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/v1/firm", "/api/v1/settings/firm"})
@RequiredArgsConstructor
public class FirmController {

	private static final Set<String> ALLOWED_CURRENCIES = Currency.getAvailableCurrencies().stream()
			.map(Currency::getCurrencyCode)
			.collect(Collectors.toUnmodifiableSet());

	private final FirmJpaRepository firmRepository;
	private final FirmSubscriptionJpaRepository subscriptionRepository;
	private final AuditLogger auditLogger;

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	@Transactional(readOnly = true)
	public FirmSettingsResponse get() {
		return toResponse(requireFirm());
	}

	@PutMapping
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public FirmSettingsResponse update(@RequestBody UpdateFirmRequest request) {
		Firm firm = requireFirm();
		Map<String, Object> before = settingsSnapshot(firm);
		if (request.name() != null && !request.name().isBlank()) {
			firm.setName(request.name().trim());
		}
		if (request.currencyCode() != null) {
			String currency = request.currencyCode().trim().toUpperCase();
			if (currency.length() != 3 || !ALLOWED_CURRENCIES.contains(currency)) {
				throw new BusinessException(ErrorCodes.INVALID_CURRENCY, "Currency must be a valid ISO 4217 code");
			}
			firm.setCurrencyCode(currency);
		}
		if (request.timezone() != null && !request.timezone().isBlank()) {
			try {
				ZoneId.of(request.timezone().trim());
			} catch (DateTimeException ex) {
				throw new BusinessException(ErrorCodes.INVALID_TIMEZONE, "Timezone must be a valid IANA zone name");
			}
			firm.setTimezone(request.timezone().trim());
		}
		if (request.financialYearStartMonth() != null) {
			if (request.financialYearStartMonth() < 1 || request.financialYearStartMonth() > 12) {
				throw new ValidationException("financialYearStartMonth", "Must be between 1 and 12");
			}
			firm.setFinancialYearStartMonth(request.financialYearStartMonth());
		}
		if (request.aiEnabled() != null) {
			firm.setAiEnabled(request.aiEnabled());
		}
		Firm saved = firmRepository.save(firm);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getId())
				.action(AuditAction.FIRM_SETTINGS_UPDATED)
				.resourceType(AuditResourceType.FIRM)
				.resourceId(saved.getId())
				.beforeState(before)
				.afterState(settingsSnapshot(saved))
				.build());
		return toResponse(saved);
	}

	private Firm requireFirm() {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		return firmRepository.findById(firmId).orElseThrow(() -> new ResourceNotFoundException("Firm", firmId));
	}

	private FirmSettingsResponse toResponse(Firm firm) {
		if (SecurityUtils.requireCurrentUser().getRole() != Role.RoleCode.ADMIN
				&& SecurityUtils.requireCurrentUser().getRole() != Role.RoleCode.ACCOUNTANT
				&& SecurityUtils.requireCurrentUser().getRole() != Role.RoleCode.AUDITOR) {
			throw new BusinessException(ErrorCodes.ACCESS_DENIED, "Firm settings are restricted");
		}
		FirmSubscription subscription = subscriptionRepository.findByFirmId(firm.getId()).orElse(null);
		return new FirmSettingsResponse(
				firm.getId(),
				firm.getName(),
				firm.getRegistrationNo(),
				firm.getCurrencyCode(),
				firm.getTimezone(),
				firm.getFinancialYearStartMonth(),
				firm.isAiEnabled(),
				subscription == null ? "STARTER" : subscription.getPlanCode(),
				subscription == null ? "ACTIVE" : subscription.getStatus().name()
		);
	}

	private static Map<String, Object> settingsSnapshot(Firm firm) {
		Map<String, Object> state = new LinkedHashMap<>();
		state.put("name", firm.getName());
		state.put("currencyCode", firm.getCurrencyCode());
		state.put("timezone", firm.getTimezone());
		state.put("financialYearStartMonth", firm.getFinancialYearStartMonth());
		state.put("aiEnabled", firm.isAiEnabled());
		return state;
	}

	public record UpdateFirmRequest(
			@NotBlank @Size(max = 200) String name,
			@Size(min = 3, max = 3) String currencyCode,
			String timezone,
			Integer financialYearStartMonth,
			Boolean aiEnabled
	) {
	}

	public record FirmSettingsResponse(
			UUID id,
			String name,
			String registrationNo,
			String currencyCode,
			String timezone,
			int financialYearStartMonth,
			boolean aiEnabled,
			String planCode,
			String subscriptionStatus
	) {
	}
}
