package com.finance.platform.controller.finance;

import com.finance.platform.finance.application.dto.invoicing.AllocatePaymentRequest;
import com.finance.platform.finance.application.dto.invoicing.ArPaymentRequest;
import com.finance.platform.finance.application.dto.invoicing.ArPaymentResponse;
import com.finance.platform.finance.application.dto.invoicing.ReverseAllocationRequest;
import com.finance.platform.finance.application.dto.invoicing.ReversePaymentRequest;
import com.finance.platform.finance.application.service.invoicing.ArPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ar/payments")
@RequiredArgsConstructor
public class ArPaymentController {

	private final ArPaymentService paymentService;

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public List<ArPaymentResponse> list() {
		return paymentService.list();
	}

	@GetMapping("/{paymentId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public ArPaymentResponse get(@PathVariable UUID paymentId) {
		return paymentService.get(paymentId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public ArPaymentResponse record(@Valid @RequestBody ArPaymentRequest request) {
		return paymentService.record(request);
	}

	@PostMapping("/{paymentId}/allocate")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public ArPaymentResponse allocate(
			@PathVariable UUID paymentId,
			@Valid @RequestBody AllocatePaymentRequest request
	) {
		return paymentService.allocate(paymentId, request);
	}

	@PostMapping("/{paymentId}/allocations/{allocationId}/reverse")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public ArPaymentResponse reverseAllocation(
			@PathVariable UUID paymentId,
			@PathVariable UUID allocationId,
			@RequestBody(required = false) ReverseAllocationRequest request
	) {
		return paymentService.reverseAllocation(
				paymentId,
				allocationId,
				request == null ? new ReverseAllocationRequest(null) : request);
	}

	@PostMapping("/{paymentId}/reverse")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public ArPaymentResponse reverse(
			@PathVariable UUID paymentId,
			@Valid @RequestBody ReversePaymentRequest request
	) {
		return paymentService.reverse(paymentId, request);
	}
}
