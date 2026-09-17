package com.finance.platform.core.observability;

import com.finance.platform.core.security.TenantContext;
import com.finance.platform.core.security.TenantContextHolder;
import org.slf4j.MDC;

import java.util.UUID;

public final class ObservabilityMdc {

	public static final String REQUEST_ID = "requestId";
	public static final String USER_ID = "userId";
	public static final String FIRM_ID = "firmId";
	public static final String MODULE = "module";
	public static final String OPERATION = "operation";

	private ObservabilityMdc() {
	}

	public static String generateRequestId() {
		return UUID.randomUUID().toString();
	}

	public static void setRequestId(String requestId) {
		if (requestId != null && !requestId.isBlank()) {
			MDC.put(REQUEST_ID, requestId);
		}
	}

	public static void enrichFromTenantContext() {
		TenantContext context = TenantContextHolder.get();
		if (context == null) {
			return;
		}
		if (context.userId() != null) {
			MDC.put(USER_ID, context.userId().toString());
		}
		if (context.firmId() != null) {
			MDC.put(FIRM_ID, context.firmId().toString());
		}
	}

	public static void setOperation(String operation) {
		if (operation != null && !operation.isBlank()) {
			MDC.put(OPERATION, operation);
		}
	}

	public static void setModule(String module) {
		if (module != null && !module.isBlank()) {
			MDC.put(MODULE, module);
		}
	}

	public static String currentRequestId() {
		String requestId = MDC.get(REQUEST_ID);
		return requestId != null ? requestId : "unknown";
	}

	public static void clear() {
		MDC.clear();
	}
}
