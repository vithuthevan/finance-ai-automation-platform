package com.finance.platform.controller;

import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.finance.application.dto.ClientPortfolioItemResponse;
import com.finance.platform.finance.application.dto.StaffWorkloadItemResponse;
import com.finance.platform.finance.application.dto.AssignPracticeWorkRequest;
import com.finance.platform.finance.application.dto.PracticeWorkAssignmentResponse;
import com.finance.platform.finance.application.dto.WorkItemResponse;
import com.finance.platform.finance.application.service.PracticeWorkAssignmentService;
import com.finance.platform.finance.application.dto.WorkSummaryResponse;
import com.finance.platform.finance.application.dto.FirmOnboardingChecklistResponse;
import com.finance.platform.finance.application.dto.MonthEndCommandCenterResponse;
import com.finance.platform.finance.application.dto.MonthEndPortfolioFocus;
import com.finance.platform.finance.application.dto.MonthEndPortfolioState;
import com.finance.platform.finance.application.service.FirmOnboardingService;
import com.finance.platform.finance.application.service.MonthEndCommandCenterService;
import com.finance.platform.finance.application.service.PracticeWorkQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work")
@RequiredArgsConstructor
@Tag(name = "Practice work", description = "Actionable work queue for accountants and administrators.")
public class WorkController {

	private final PracticeWorkQueueService workQueueService;
	private final MonthEndCommandCenterService monthEndCommandCenterService;
	private final FirmOnboardingService firmOnboardingService;
	private final PracticeWorkAssignmentService assignmentService;

	@GetMapping("/summary")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "Aggregated workflow counts for dashboard cards")
	public WorkSummaryResponse summary() {
		return workQueueService.summary();
	}

	@GetMapping("/my")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "Paginated actionable work for the authenticated user")
	public PageResponse<WorkItemResponse> myWork(
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String priority,
			@RequestParam(required = false) UUID clientId,
			@RequestParam(required = false) Boolean overdueOnly,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return workQueueService.myWork(type, priority, clientId, overdueOnly, page, size);
	}

	@GetMapping("/portfolio")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "Multi-client practice workflow view")
	public List<ClientPortfolioItemResponse> portfolio(@RequestParam(required = false) String query) {
		return workQueueService.portfolio(query);
	}

	@GetMapping("/staff-workload")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Operational workload by accountant")
	public List<StaffWorkloadItemResponse> staffWorkload() {
		return workQueueService.staffWorkload();
	}

	@GetMapping("/month-end-command-center")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "Firm month-end portfolio readiness with actionable blockers")
	public MonthEndCommandCenterResponse monthEndCommandCenter(
			@RequestParam(required = false) Integer year,
			@RequestParam(required = false) Integer month,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) MonthEndPortfolioState state,
			@RequestParam(required = false) MonthEndPortfolioFocus focus,
			@RequestParam(required = false) UUID accountantUserId,
			@RequestParam(required = false) UUID clientId
	) {
		return monthEndCommandCenterService.commandCenter(year, month, query, state, focus, accountantUserId, clientId);
	}

	@PostMapping("/assignments")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public PracticeWorkAssignmentResponse assign(@Valid @RequestBody AssignPracticeWorkRequest request) {
		return assignmentService.assign(request);
	}

	@PostMapping("/assignments/{assignmentId}/clear")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public PracticeWorkAssignmentResponse clear(@PathVariable UUID assignmentId) {
		return assignmentService.clearAssignment(assignmentId);
	}

	@PostMapping("/assignments/{assignmentId}/complete")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public PracticeWorkAssignmentResponse complete(@PathVariable UUID assignmentId) {
		return assignmentService.complete(assignmentId);
	}

	@GetMapping("/onboarding-checklist")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "New firm setup checklist progress")
	public FirmOnboardingChecklistResponse onboardingChecklist() {
		return firmOnboardingService.checklist();
	}
}
