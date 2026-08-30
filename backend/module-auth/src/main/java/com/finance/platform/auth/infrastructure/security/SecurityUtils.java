package com.finance.platform.auth.infrastructure.security;

import com.finance.platform.core.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

	private SecurityUtils() {
	}

	public static SecurityUser requireCurrentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof SecurityUser securityUser) {
			return securityUser;
		}
		throw new BusinessException("Not authenticated");
	}
}
