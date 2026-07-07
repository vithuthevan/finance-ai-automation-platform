package com.finance.platform.finance.application.service;

import com.finance.platform.core.exception.DuplicateResourceException;
import com.finance.platform.finance.domain.model.Firm;
import com.finance.platform.finance.infrastructure.persistence.FirmJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FirmService {

	private final FirmJpaRepository firmRepository;

	@Transactional
	public Firm createFirm(String name, String registrationNo) {
		if (firmRepository.existsByName(name)) {
			throw new DuplicateResourceException("Firm", "name", name);
		}

		Firm firm = Firm.builder()
				.name(name)
				.registrationNo(registrationNo)
				.active(true)
				.build();

		return firmRepository.save(firm);
	}
}
