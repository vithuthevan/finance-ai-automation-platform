package com.finance.platform.finance.application.service;

import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.auth.infrastructure.security.SecurityUser;
import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.finance.application.dto.FirmOnboardingChecklistResponse;
import com.finance.platform.finance.application.dto.OnboardingStepResponse;
import com.finance.platform.finance.domain.model.Firm;
import com.finance.platform.finance.infrastructure.persistence.CategoryJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ClientJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.FirmJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ClientMonthlyEvidenceJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ReceiptJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FirmOnboardingService {

	private final FirmJpaRepository firmRepository;
	private final ClientJpaRepository clientRepository;
	private final UserJpaRepository userRepository;
	private final CategoryJpaRepository categoryRepository;
	private final ReceiptJpaRepository receiptRepository;
	private final ClientMonthlyEvidenceJpaRepository monthlyEvidenceRepository;

	@Transactional(readOnly = true)
	public FirmOnboardingChecklistResponse checklist() {
		SecurityUser user = SecurityUtils.requireCurrentUser();
		if (user.getRole() != Role.RoleCode.ADMIN) {
			return new FirmOnboardingChecklistResponse(user.getFirmId(), null, List.of(), 0, 0);
		}
		UUID firmId = user.getFirmId();
		String firmName = firmRepository.findById(firmId).map(Firm::getName).orElse(null);

		long clients = clientRepository.findByFirmIdAndDeletedAtIsNull(firmId).size();
		long categories = categoryRepository.findAllByFirmIdAndDeletedAtIsNull(firmId).stream()
				.filter(category -> category.getClient() == null)
				.count();
		long staff = userRepository.findByFirmIdAndDeletedAtIsNull(firmId).stream()
				.filter(u -> u.getRole().getCode() == Role.RoleCode.ACCOUNTANT || u.getRole().getCode() == Role.RoleCode.AUDITOR)
				.count();
		long owners = userRepository.findByFirmIdAndDeletedAtIsNull(firmId).stream()
				.filter(u -> u.getRole().getCode() == Role.RoleCode.BUSINESS_OWNER)
				.count();
		long documents = receiptRepository.findByFirmIdAndDeletedAtIsNull(firmId, PageRequest.of(0, 1)).getTotalElements();
		long evidenceChecklists = monthlyEvidenceRepository.countByFirmIdAndActiveTrue(firmId);

		List<OnboardingStepResponse> steps = new ArrayList<>();
		steps.add(step("CREATE_CLIENT", "Create your first client", clients > 0, "/app/clients"));
		steps.add(step("CONFIRM_CATEGORIES", "Confirm expense & income categories", categories >= 5, "/app/categories"));
		steps.add(step("ADD_STAFF", "Add an accountant or auditor", staff > 0, "/app/users"));
		steps.add(step("INVITE_OWNER", "Create a business owner login", owners > 0, "/app/users"));
		steps.add(step("CONFIGURE_EVIDENCE", "Configure monthly evidence checklist", evidenceChecklists > 0, "/app/clients"));
		steps.add(step("UPLOAD_DOCUMENT", "Upload first document evidence", documents > 0, "/app/documents"));
		steps.add(step("PREPARE_CLOSE", "Open month-end command center", clients > 0, "/app/month-end"));

		long completed = steps.stream().filter(OnboardingStepResponse::completed).count();
		return new FirmOnboardingChecklistResponse(
				firmId,
				firmName,
				steps,
				(int) completed,
				steps.size()
		);
	}

	private static OnboardingStepResponse step(String code, String label, boolean done, String path) {
		return new OnboardingStepResponse(code, label, done, path);
	}
}
