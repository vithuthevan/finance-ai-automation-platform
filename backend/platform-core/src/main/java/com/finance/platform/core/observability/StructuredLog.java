package com.finance.platform.core.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

public final class StructuredLog {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private StructuredLog() {
	}

	public static void info(Logger logger, String operation, Map<String, Object> fields) {
		logger.info(format(operation, "INFO", fields));
	}

	public static void warn(Logger logger, String operation, Map<String, Object> fields) {
		logger.warn(format(operation, "WARN", fields));
	}

	public static void error(Logger logger, String operation, Map<String, Object> fields, Throwable cause) {
		if (cause != null) {
			logger.error(format(operation, "ERROR", fields), cause);
		} else {
			logger.error(format(operation, "ERROR", fields));
		}
	}

	public static Map<String, Object> baseFields(String status) {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("requestId", ObservabilityMdc.currentRequestId());
		fields.put("status", status);
		String userId = org.slf4j.MDC.get(ObservabilityMdc.USER_ID);
		if (userId != null) {
			fields.put("userId", userId);
		}
		String firmId = org.slf4j.MDC.get(ObservabilityMdc.FIRM_ID);
		if (firmId != null) {
			fields.put("firmId", firmId);
		}
		return fields;
	}

	private static String format(String operation, String level, Map<String, Object> fields) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("operation", operation);
		payload.put("level", level);
		if (fields != null) {
			payload.putAll(fields);
		}
		try {
			return OBJECT_MAPPER.writeValueAsString(payload);
		} catch (JsonProcessingException ex) {
			return "{\"operation\":\"" + operation + "\",\"level\":\"" + level + "\"}";
		}
	}
}
