package com.finance.platform.auth.infrastructure.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

	/**
	 * When true, new self-registrations must verify email ownership before login.
	 */
	private boolean emailVerificationRequired = false;
}
