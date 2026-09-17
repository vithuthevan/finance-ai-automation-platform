package com.finance.platform.core.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class SecurityEventLogger {

	public void loginSuccess(UUID userId, UUID firmId, String email) {
		Map<String, Object> fields = baseAuthFields(userId, firmId);
		fields.put("email", email);
		StructuredLog.info(log, "LOGIN_SUCCESS", fields);
	}

	public void loginFailure(String email, String reason) {
		Map<String, Object> fields = StructuredLog.baseFields("FAILURE");
		fields.put("email", email);
		fields.put("reason", reason);
		StructuredLog.warn(log, "LOGIN_FAILURE", fields);
	}

	public void logout(UUID userId, UUID firmId, String email) {
		Map<String, Object> fields = baseAuthFields(userId, firmId);
		fields.put("email", email);
		StructuredLog.info(log, "LOGOUT", fields);
	}

	public void passwordResetRequested(UUID userId, UUID firmId, String email) {
		Map<String, Object> fields = baseAuthFields(userId, firmId);
		fields.put("email", email);
		StructuredLog.info(log, "PASSWORD_RESET_REQUESTED", fields);
	}

	public void passwordResetCompleted(UUID userId, UUID firmId, String email) {
		Map<String, Object> fields = baseAuthFields(userId, firmId);
		fields.put("email", email);
		StructuredLog.info(log, "PASSWORD_RESET_COMPLETED", fields);
	}

	public void tokenReuseDetected(UUID userId, UUID firmId, String email) {
		Map<String, Object> fields = baseAuthFields(userId, firmId);
		fields.put("email", email);
		StructuredLog.warn(log, "TOKEN_REUSE_DETECTED", fields);
	}

	public void emailVerified(UUID userId, UUID firmId, String email) {
		Map<String, Object> fields = baseAuthFields(userId, firmId);
		fields.put("email", email);
		StructuredLog.info(log, "EMAIL_VERIFIED", fields);
	}

	public void jwtRejected(String clientIp, String reason) {
		Map<String, Object> fields = StructuredLog.baseFields("FAILURE");
		fields.put("clientIp", clientIp);
		fields.put("reason", reason);
		StructuredLog.warn(log, "JWT_REJECTED", fields);
	}

	public void permissionDenied(String path, String method) {
		Map<String, Object> fields = StructuredLog.baseFields("FAILURE");
		fields.put("path", path);
		fields.put("method", method);
		StructuredLog.warn(log, "PERMISSION_DENIED", fields);
	}

	public void authenticationRequired(String path, String method) {
		Map<String, Object> fields = StructuredLog.baseFields("FAILURE");
		fields.put("path", path);
		fields.put("method", method);
		StructuredLog.warn(log, "AUTHENTICATION_REQUIRED", fields);
	}

	private static Map<String, Object> baseAuthFields(UUID userId, UUID firmId) {
		Map<String, Object> fields = StructuredLog.baseFields("SUCCESS");
		if (userId != null) {
			fields.put("userId", userId.toString());
		}
		if (firmId != null) {
			fields.put("firmId", firmId.toString());
		}
		return fields;
	}
}
