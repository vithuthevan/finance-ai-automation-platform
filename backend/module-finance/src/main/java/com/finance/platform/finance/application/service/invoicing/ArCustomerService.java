package com.finance.platform.finance.application.service.invoicing;

import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.finance.application.dto.invoicing.ArCustomerRequest;
import com.finance.platform.finance.application.dto.invoicing.ArCustomerResponse;
import com.finance.platform.finance.domain.model.invoicing.ArCustomer;
import com.finance.platform.finance.infrastructure.persistence.ArCustomerJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ClientJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArCustomerService {

	private final ArCustomerJpaRepository customerRepository;
	private final ClientJpaRepository clientRepository;

	@Transactional(readOnly = true)
	public List<ArCustomerResponse> list(boolean includeInactive) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		List<ArCustomer> rows = includeInactive
				? customerRepository.findByFirmIdOrderByNameAsc(firmId)
				: customerRepository.findByFirmIdAndActiveTrueOrderByNameAsc(firmId);
		return rows.stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public ArCustomerResponse get(UUID customerId) {
		return toResponse(requireCustomer(customerId));
	}

	@Transactional
	public ArCustomerResponse create(ArCustomerRequest request) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		validateClientLink(firmId, request.clientId());
		ArCustomer customer = ArCustomer.builder()
				.name(request.name().trim())
				.email(request.email())
				.clientId(request.clientId())
				.paymentTermsDays(request.paymentTermsDays() > 0 ? request.paymentTermsDays() : 30)
				.active(true)
				.build();
		customer.setFirmId(firmId);
		return toResponse(customerRepository.save(customer));
	}

	@Transactional
	public ArCustomerResponse update(UUID customerId, ArCustomerRequest request) {
		ArCustomer customer = requireCustomer(customerId);
		validateClientLink(customer.getFirmId(), request.clientId());
		customer.setName(request.name().trim());
		customer.setEmail(request.email());
		customer.setClientId(request.clientId());
		if (request.paymentTermsDays() > 0) {
			customer.setPaymentTermsDays(request.paymentTermsDays());
		}
		return toResponse(customerRepository.save(customer));
	}

	@Transactional
	public void deactivate(UUID customerId) {
		ArCustomer customer = requireCustomer(customerId);
		customer.setActive(false);
		customerRepository.save(customer);
	}

	ArCustomer requireCustomer(UUID customerId) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		return customerRepository.findByIdAndFirmId(customerId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("ArCustomer", customerId));
	}

	private void validateClientLink(UUID firmId, UUID clientId) {
		if (clientId == null) {
			return;
		}
		clientRepository.findByIdAndFirmId(clientId, firmId)
				.orElseThrow(() -> new ValidationException("clientId", "Client not found in this firm"));
	}

	private ArCustomerResponse toResponse(ArCustomer customer) {
		return new ArCustomerResponse(
				customer.getId(),
				customer.getName(),
				customer.getEmail(),
				customer.getClientId(),
				customer.getPaymentTermsDays(),
				customer.isActive()
		);
	}
}
