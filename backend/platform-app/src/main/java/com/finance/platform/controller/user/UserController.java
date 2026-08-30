package com.finance.platform.controller.user;

import com.finance.platform.auth.application.dto.ChangePasswordRequest;
import com.finance.platform.auth.application.dto.ClientAccessResponse;
import com.finance.platform.auth.application.dto.CreateUserRequest;
import com.finance.platform.auth.application.dto.ReplaceClientAccessRequest;
import com.finance.platform.auth.application.dto.UpdateUserRequest;
import com.finance.platform.auth.application.dto.UserResponse;
import com.finance.platform.auth.application.service.UserService;
import com.finance.platform.core.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
	@PreAuthorize("hasRole('ADMIN')")
	public PageResponse<UserResponse> listUsers(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return userService.listFirmUsers(page, size);
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

	@PutMapping("/{userId}")
	@PreAuthorize("hasRole('ADMIN')")
	public UserResponse updateUser(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRequest request) {
		return userService.updateUser(userId, request);
	}

	@PostMapping("/{userId}/activate")
	@PreAuthorize("hasRole('ADMIN')")
	public UserResponse activateUser(@PathVariable UUID userId) {
		return userService.setActive(userId, true);
	}

	@PostMapping("/{userId}/deactivate")
	@PreAuthorize("hasRole('ADMIN')")
	public UserResponse deactivateUser(@PathVariable UUID userId) {
		return userService.setActive(userId, false);
	}

	@PutMapping("/{userId}/client-access")
	@PreAuthorize("hasRole('ADMIN')")
	public List<ClientAccessResponse> replaceClientAccess(
			@PathVariable UUID userId,
			@Valid @RequestBody ReplaceClientAccessRequest request
	) {
		return userService.replaceClientAccess(userId, request);
	}

	@PostMapping("/me/password")
	@PreAuthorize("isAuthenticated()")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
		userService.changePassword(request);
	}
}
