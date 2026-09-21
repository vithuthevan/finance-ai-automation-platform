package com.finance.platform.finance.application.service.invoicing;

import com.finance.platform.finance.domain.model.invoicing.FirmInvoiceNumberSequence;
import com.finance.platform.finance.infrastructure.persistence.FirmInvoiceNumberSequenceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceNumberSequenceService {

	private final FirmInvoiceNumberSequenceJpaRepository sequenceRepository;

	@Transactional
	public String nextInvoiceNumber(UUID firmId) {
		FirmInvoiceNumberSequence sequence = sequenceRepository.findForUpdate(firmId)
				.orElseGet(() -> {
					FirmInvoiceNumberSequence created = new FirmInvoiceNumberSequence();
					created.setFirmId(firmId);
					created.setNextNumber(1L);
					return sequenceRepository.save(created);
				});
		long assigned = sequence.getNextNumber();
		sequence.setNextNumber(assigned + 1);
		sequenceRepository.save(sequence);
		return "INV-" + assigned;
	}
}
