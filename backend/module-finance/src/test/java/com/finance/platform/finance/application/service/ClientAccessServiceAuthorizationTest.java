package com.finance.platform.finance.application.service;

import com.finance.platform.auth.api.UserFacade;
import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.domain.model.Role.RoleCode;
import com.finance.platform.auth.domain.model.UserClientAccess;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.auth.infrastructure.security.SecurityUser;
import com.finance.platform.core.subscription.SubscriptionQuotaGuard;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.infrastructure.persistence.ClientJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientAccessServiceAuthorizationTest {

	@Mock
	private ClientJpaRepository clientRepository;
	@Mock
	private UserFacade userFacade;
	@Mock
	private UserJpaRepository userRepository;
	@Mock
	private SubscriptionQuotaGuard subscriptionQuotaGuard;

	@InjectMocks
	private ClientAccessService clientAccessService;

	private final UUID firmId = UUID.randomUUID();
	private final UUID clientId = UUID.randomUUID();
	private final UUID userId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		Client client = Client.builder().name("Test Client").active(true).build();
		client.setId(clientId);
		client.setFirmId(firmId);
		when(clientRepository.findByIdAndFirmIdAndDeletedAtIsNull(clientId, firmId)).thenReturn(Optional.of(client));
		when(userFacade.hasAccessToClient(userId, clientId)).thenReturn(true);
		when(userFacade.getAssignedAccessType(userId, clientId)).thenReturn(Optional.of(UserClientAccess.AccessType.FULL));
		doNothing().when(subscriptionQuotaGuard).assertCanWrite(firmId);
	}

	@AfterEach
	void clearSecurity() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void businessOwnerFull_deniedLedgerWrite_allowedUpload() {
		authenticate(Role.RoleCode.BUSINESS_OWNER);
		assertThrows(AccessDeniedException.class, () -> clientAccessService.requireLedgerWriteAccess(clientId));
		assertDoesNotThrow(() -> clientAccessService.requireUploadAccess(clientId));
	}

	@Test
	void businessOwnerUploadOnly_deniedLedgerWrite_allowedUpload() {
		when(userFacade.getAssignedAccessType(userId, clientId)).thenReturn(Optional.of(UserClientAccess.AccessType.UPLOAD_ONLY));
		authenticate(Role.RoleCode.BUSINESS_OWNER);
		assertThrows(AccessDeniedException.class, () -> clientAccessService.requireLedgerWriteAccess(clientId));
		assertDoesNotThrow(() -> clientAccessService.requireUploadAccess(clientId));
	}

	@Test
	void auditor_deniedLedgerWrite() {
		authenticate(Role.RoleCode.AUDITOR);
		assertThrows(AccessDeniedException.class, () -> clientAccessService.requireLedgerWriteAccess(clientId));
	}

	@Test
	void accountant_allowedLedgerWrite() {
		authenticate(Role.RoleCode.ACCOUNTANT);
		assertDoesNotThrow(() -> clientAccessService.requireLedgerWriteAccess(clientId));
	}

	private void authenticate(RoleCode roleCode) {
		Role role = Role.builder().code(roleCode).name(roleCode.name()).build();
		User user = User.builder()
				.email("user@example.com")
				.passwordHash("hash")
				.fullName("Test User")
				.role(role)
				.active(true)
				.build();
		user.setId(userId);
		user.setFirmId(firmId);
		SecurityUser principal = new SecurityUser(user);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
	}
}
