package com.finance.platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

	private final DataSource dataSource;

	public HealthController(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@GetMapping("/api/v1/health")
	public Map<String, String> health() {
		return Map.of("status", "UP", "application", "finance-platform");
	}

	@GetMapping("/api/v1/health/ready")
	public ResponseEntity<Map<String, String>> ready() {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("application", "finance-platform");
		try (Connection connection = dataSource.getConnection()) {
			if (!connection.isValid(2)) {
				body.put("status", "NOT_READY");
				body.put("database", "INVALID");
				return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
			}
			body.put("status", "READY");
			body.put("database", "UP");
			return ResponseEntity.ok(body);
		} catch (Exception ex) {
			body.put("status", "NOT_READY");
			body.put("database", "DOWN");
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
		}
	}
}
