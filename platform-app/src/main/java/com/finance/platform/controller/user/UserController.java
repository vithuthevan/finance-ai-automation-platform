package com.finance.platform.controller.user;

import com.finance.platform.auth.application.dto.CreateUserRequest;
import com.finance.platform.auth.application.dto.UserResponse;
import com.finance.platform.auth.application.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public List<UserResponse> listUsers() {
		return userService.listFirmUsers();
	}

	@GetMapping("/{userId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	public UserResponse getUser(@PathVariable UUID userId) {
		return userService.getUser(userId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMIN')")
	public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
		return userService.createUser(request);
	}
}
