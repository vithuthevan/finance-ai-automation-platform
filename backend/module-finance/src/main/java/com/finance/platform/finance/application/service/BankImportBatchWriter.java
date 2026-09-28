package com.finance.platform.finance.application.service;

import com.finance.platform.finance.domain.model.BankImport;
import com.finance.platform.finance.infrastructure.persistence.BankImportJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BankImportBatchWriter {

	private final BankImportJpaRepository importRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = DataIntegrityViolationException.class)
	public BankImport tryPersist(BankImport batch) {
		try {
			return importRepository.saveAndFlush(batch);
		} catch (DataIntegrityViolationException ex) {
			return null;
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public Optional<BankImport> findByAccountAndChecksum(UUID bankAccountId, String checksum) {
		return importRepository.findByBankAccount_IdAndChecksum(bankAccountId, checksum);
	}
}
