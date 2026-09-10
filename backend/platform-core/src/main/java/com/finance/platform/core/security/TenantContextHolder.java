package com.finance.platform.core.security;

import com.finance.platform.core.exception.BusinessException;

public final class TenantContextHolder {

	private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

	private TenantContextHolder() {
	}

	public static void set(TenantContext context) {
		CONTEXT.set(context);
	}

	public static TenantContext get() {
		return CONTEXT.get();
	}

	public static TenantContext require() {
		TenantContext context = CONTEXT.get();
		if (context == null || context.userId() == null) {
			throw new BusinessException("Not authenticated");
		}
		return context;
	}

	public static void clear() {
		CONTEXT.remove();
	}
}
