package com.finance.platform.finance.application.service;

import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.exception.DuplicateResourceException;
import com.finance.platform.finance.domain.model.Firm;
import com.finance.platform.finance.domain.model.FirmSubscription;
import com.finance.platform.finance.infrastructure.persistence.FirmJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.FirmSubscriptionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FirmService {

	private final FirmJpaRepository firmRepository;
	private final FirmSubscriptionJpaRepository subscriptionRepository;
	private final AuditLogger auditLogger;

	@Transactional
	public Firm createFirm(String name, String registrationNo) {
		if (firmRepository.existsByName(name)) {
			throw new DuplicateResourceException("Firm", "name", name);
		}

		Firm firm = Firm.builder()
				.name(name)
				.registrationNo(registrationNo)
				.active(true)
				.build();

		Firm saved = firmRepository.save(firm);
		subscriptionRepository.save(FirmSubscription.builder()
				.firmId(saved.getId())
				.planCode("STANDARD")
				.status(FirmSubscription.SubscriptionStatus.TRIAL)
				.build());
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("name", saved.getName());
		after.put("registrationNo", saved.getRegistrationNo());
		after.put("active", saved.isActive());

		auditLogger.record(AuditEvent.builder()
				.firmId(saved.getId())
				.action(AuditAction.FIRM_REGISTERED)
				.resourceType(AuditResourceType.FIRM)
				.resourceId(saved.getId())
				.afterState(after)
				.build());
		return saved;
	}
}
