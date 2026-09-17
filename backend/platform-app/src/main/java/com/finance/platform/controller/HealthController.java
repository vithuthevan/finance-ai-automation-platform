package com.finance.platform.controller;

import com.finance.platform.core.observability.ObservabilityMdc;
import com.finance.platform.core.observability.StructuredLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
public class HealthController {

	private final DataSource dataSource;

	public HealthController(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@GetMapping("/api/v1/health")
	public Map<String, String> health() {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("status", "UP");
		body.put("application", "finance-platform");
		body.put("requestId", ObservabilityMdc.currentRequestId());
		return body;
	}

	@GetMapping("/api/v1/health/ready")
	public ResponseEntity<Map<String, String>> ready() {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("application", "finance-platform");
		body.put("requestId", ObservabilityMdc.currentRequestId());
		try (Connection connection = dataSource.getConnection()) {
			if (!connection.isValid(2)) {
				body.put("status", "NOT_READY");
				body.put("database", "INVALID");
				StructuredLog.warn(log, "HEALTH_READINESS_CHECK",
						StructuredLog.baseFields("NOT_READY"));
				return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
			}
			body.put("status", "READY");
			body.put("database", "UP");
			return ResponseEntity.ok(body);
		} catch (Exception ex) {
			body.put("status", "NOT_READY");
			body.put("database", "DOWN");
			StructuredLog.error(log, "HEALTH_READINESS_CHECK",
					StructuredLog.baseFields("NOT_READY"), ex);
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
		}
	}
}
