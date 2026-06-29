package com.finance.platform.application.service;

import com.finance.platform.auth.application.dto.LoginResponse;
import com.finance.platform.auth.application.dto.RegisterRequest;
import com.finance.platform.auth.application.service.AuthService;
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
	private final AuthService authService;

	@Transactional
	public LoginResponse registerFirmAdmin(RegisterRequest request) {
		Firm firm = firmService.createFirm(request.firmName(), request.registrationNo());
		User user = authService.registerAdmin(
				firm.getId(),
				request.email(),
				request.password(),
				request.fullName()
		);
		return authService.buildAuthResponse(user);
	}
}
