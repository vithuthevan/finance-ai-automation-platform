package com.finance.platform.finance.application.service;

import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.finance.application.dto.BankAccountResponse;
import com.finance.platform.finance.application.dto.CreateBankAccountRequest;
import com.finance.platform.finance.application.dto.UpdateBankAccountRequest;
import com.finance.platform.finance.domain.model.BankAccount;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.infrastructure.persistence.BankAccountJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BankAccountService {

	private final BankAccountJpaRepository bankAccountRepository;
	private final ClientAccessService clientAccessService;
	private final AuditLogger auditLogger;

	@Transactional(readOnly = true)
	public List<BankAccountResponse> list(UUID clientId, boolean activeOnly) {
		clientAccessService.requireReadAccess(clientId);
		List<BankAccount> accounts = activeOnly
				? bankAccountRepository.findByClient_IdAndActiveTrueOrderByBankNameAscAccountNameAsc(clientId)
				: bankAccountRepository.findByClient_IdOrderByBankNameAscAccountNameAsc(clientId);
		return accounts.stream().map(BankAccountResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public BankAccountResponse get(UUID clientId, UUID accountId) {
		clientAccessService.requireReadAccess(clientId);
		return BankAccountResponse.from(requireAccount(clientId, accountId));
	}

	@Transactional
	public BankAccountResponse create(UUID clientId, CreateBankAccountRequest request) {
		Client client = clientAccessService.requireWriteAccess(clientId);
		validate(request.bankName(), request.accountName());
		BankAccount account = BankAccount.builder()
				.client(client)
				.bankName(request.bankName().trim())
				.accountName(request.accountName().trim())
				.maskedAccountNumber(mask(request.maskedAccountNumber()))
				.currency(request.currency() == null || request.currency().isBlank() ? "LKR" : request.currency().trim().toUpperCase())
				.active(true)
				.build();
		account.setFirmId(client.getFirmId());
		BankAccount saved = bankAccountRepository.save(account);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.BANK_ACCOUNT_CREATED)
				.resourceType(AuditResourceType.BANK)
				.resourceId(saved.getId())
				.clientId(clientId)
				.afterState(Map.of("bankName", saved.getBankName(), "accountName", saved.getAccountName()))
				.build());
		return BankAccountResponse.from(saved);
	}

	@Transactional
	public BankAccountResponse update(UUID clientId, UUID accountId, UpdateBankAccountRequest request) {
		clientAccessService.requireWriteAccess(clientId);
		BankAccount account = requireAccount(clientId, accountId);
		if (request.bankName() != null && !request.bankName().isBlank()) {
			account.setBankName(request.bankName().trim());
		}
		if (request.accountName() != null && !request.accountName().isBlank()) {
			account.setAccountName(request.accountName().trim());
		}
		if (request.maskedAccountNumber() != null) {
			account.setMaskedAccountNumber(mask(request.maskedAccountNumber()));
		}
		if (request.currency() != null && !request.currency().isBlank()) {
			account.setCurrency(request.currency().trim().toUpperCase());
		}
		if (request.active() != null) {
			account.setActive(request.active());
		}
		BankAccount saved = bankAccountRepository.save(account);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.BANK_ACCOUNT_UPDATED)
				.resourceType(AuditResourceType.BANK)
				.resourceId(saved.getId())
				.clientId(clientId)
				.build());
		return BankAccountResponse.from(saved);
	}

	BankAccount requireAccount(UUID clientId, UUID accountId) {
		return bankAccountRepository.findByIdAndClient_Id(accountId, clientId)
				.orElseThrow(() -> new ResourceNotFoundException("Bank account", accountId));
	}

	private static void validate(String bankName, String accountName) {
		if (bankName == null || bankName.isBlank()) {
			throw new ValidationException("bankName", "Bank name is required");
		}
		if (accountName == null || accountName.isBlank()) {
			throw new ValidationException("accountName", "Account name is required");
		}
	}

	private static String mask(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String digits = raw.replaceAll("\\s+", "");
		if (digits.length() <= 4) {
			return "****" + digits;
		}
		return "****" + digits.substring(digits.length() - 4);
	}
}
