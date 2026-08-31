package com.finance.platform.controller.finance;

import com.finance.platform.ai.api.AiExtractionFacade;
import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI metrics", description = "Firm-level extraction and suggestion outcome counts. ADMIN only.")
public class AiMetricsController {

	private final AiExtractionFacade aiExtractionFacade;

	@GetMapping("/metrics")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "AI processing success, failure, and review outcomes for the current firm")
	public AiExtractionFacade.AiUsageMetrics metrics() {
		return aiExtractionFacade.usageMetrics(SecurityUtils.requireCurrentUser().getFirmId());
	}
}
