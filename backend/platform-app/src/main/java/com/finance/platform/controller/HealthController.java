package com.finance.platform.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

	@GetMapping("/api/v1/health")
	public Map<String, String> health() {
		return Map.of("status", "UP", "application", "finance-platform");
	}

	@GetMapping("/api/v1/health/ready")
	public Map<String, String> ready() {
		return Map.of("status", "READY", "application", "finance-platform");
	}
}
