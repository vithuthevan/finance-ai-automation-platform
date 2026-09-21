package com.finance.platform.platformadmin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformAdminBootstrapRunner implements ApplicationRunner {

	private final PlatformAdminService platformAdminService;
	private final PlatformAdminGrantJpaRepository grantRepository;
	private final com.finance.platform.auth.infrastructure.persistence.UserJpaRepository userRepository;
	private final Environment environment;

	@Value("${app.platform.bootstrap-admin-enabled:false}")
	private boolean bootstrapEnabled;

	@Value("${app.platform.bootstrap-admin-email:}")
	private String bootstrapEmail;

	@Override
	public void run(ApplicationArguments args) {
		boolean emailConfigured = bootstrapEmail != null && !bootstrapEmail.isBlank();
		if (!emailConfigured) {
			return;
		}
		if (grantRepository.countByActiveTrue() > 0) {
			if (bootstrapEnabled) {
				log.warn(
						"Platform admin bootstrap skipped: active platform admin grant(s) already exist. "
								+ "Disable app.platform.bootstrap-admin-enabled and clear APP_PLATFORM_ADMIN_BOOTSTRAP_EMAIL.");
			}
			return;
		}
		if (!bootstrapEnabled) {
			log.warn(
					"APP_PLATFORM_ADMIN_BOOTSTRAP_EMAIL is set but app.platform.bootstrap-admin-enabled=false. "
							+ "Set app.platform.bootstrap-admin-enabled=true for one-time bootstrap when no admins exist.");
			return;
		}
		String normalizedEmail = bootstrapEmail.trim();
		userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail).ifPresentOrElse(user -> {
			if (!platformAdminService.isPlatformAdmin(user.getId())) {
				platformAdminService.bootstrapGrant(user.getId(), "One-time bootstrap from APP_PLATFORM_ADMIN_BOOTSTRAP_EMAIL");
				log.info("Platform administrator bootstrap grant created for user {}", user.getId());
			}
			if (isProdProfile()) {
				log.warn(
						"Platform admin bootstrap completed. Set app.platform.bootstrap-admin-enabled=false and "
								+ "remove APP_PLATFORM_ADMIN_BOOTSTRAP_EMAIL before the next deploy.");
			}
		}, () -> log.warn("Platform admin bootstrap email configured but no user found: {}", normalizedEmail));
	}

	private boolean isProdProfile() {
		return Arrays.stream(environment.getActiveProfiles()).anyMatch(p -> "prod".equalsIgnoreCase(p));
	}
}
