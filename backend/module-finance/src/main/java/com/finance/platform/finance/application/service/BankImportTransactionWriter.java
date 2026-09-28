package com.finance.platform.finance.application.service;

import com.finance.platform.finance.domain.model.BankTransaction;
import com.finance.platform.finance.infrastructure.persistence.BankTransactionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BankImportTransactionWriter {

	private final BankTransactionJpaRepository bankTransactionRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = DataIntegrityViolationException.class)
	public BankTransaction tryPersist(BankTransaction txn) {
		try {
			return bankTransactionRepository.saveAndFlush(txn);
		} catch (RuntimeException ex) {
			if (isUniqueViolation(ex)) {
				return null;
			}
			throw ex;
		}
	}

	private static boolean isUniqueViolation(RuntimeException ex) {
		if (ex instanceof DataIntegrityViolationException) {
			return true;
		}
		Throwable cause = ex;
		while (cause != null) {
			if (cause instanceof DataIntegrityViolationException) {
				return true;
			}
			String message = cause.getMessage();
			if (message != null && message.contains("uq_bank_txn_account_row_hash")) {
				return true;
			}
			cause = cause.getCause();
		}
		return false;
	}
}
