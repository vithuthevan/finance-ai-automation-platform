package com.finance.platform.platformadmin;

import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformAdminBootstrapRunner implements ApplicationRunner {

	private final PlatformAdminService platformAdminService;
	private final UserJpaRepository userRepository;

	@Value("${app.platform.bootstrap-admin-email:}")
	private String bootstrapEmail;

	@Override
	public void run(ApplicationArguments args) {
		if (bootstrapEmail == null || bootstrapEmail.isBlank()) {
			return;
		}
		userRepository.findByEmailAndDeletedAtIsNull(bootstrapEmail.trim()).ifPresentOrElse(user -> {
			if (!platformAdminService.isPlatformAdmin(user.getId())) {
				platformAdminService.bootstrapGrant(user.getId(), "Bootstrap from APP_PLATFORM_ADMIN_BOOTSTRAP_EMAIL");
				log.info("Platform administrator bootstrap grant created for user {}", user.getId());
			}
		}, () -> log.warn("Platform admin bootstrap email configured but no user found: {}", bootstrapEmail));
	}
}
