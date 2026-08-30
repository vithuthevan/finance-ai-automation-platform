package com.finance.platform.core.config;

import com.finance.platform.core.security.TenantContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

	@Bean
	AuditorAware<UUID> auditorAware() {
		return () -> Optional.ofNullable(TenantContextHolder.get()).map(ctx -> ctx.userId());
	}
}
