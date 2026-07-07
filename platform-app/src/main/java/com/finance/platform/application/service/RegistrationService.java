package com.finance.platform.application.service;

import com.finance.platform.auth.application.dto.RegisterRequest;
import com.finance.platform.auth.application.dto.RegisterResponse;
import com.finance.platform.auth.application.service.UserRegistrationService;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.finance.application.service.FirmService;
import com.finance.platform.finance.domain.model.Firm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationService {

	private final FirmService firmService;
	private final UserRegistrationService userRegistrationService;

	@Transactional
	public RegisterResponse register(RegisterRequest request) {
		Firm firm = firmService.createFirm(request.firmName(), request.registrationNo());
		User user = userRegistrationService.registerAdmin(
				firm.getId(),
				request.email(),
				request.password(),
				request.fullName()
		);

		return new RegisterResponse(
				user.getId(),
				firm.getId(),
				firm.getName(),
				user.getEmail(),
				user.getFullName(),
				user.getRole().getCode().name()
		);
	}
}
