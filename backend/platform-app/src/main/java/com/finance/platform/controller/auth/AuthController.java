package com.finance.platform.controller.auth;

import com.finance.platform.auth.application.dto.UserProfileResponse;
import com.finance.platform.auth.application.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final UserService userService;

	@GetMapping("/me")
	public UserProfileResponse profile() {
		return userService.getProfile();
	}
}
