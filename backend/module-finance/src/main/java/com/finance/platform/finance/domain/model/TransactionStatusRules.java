package com.finance.platform.finance.domain.model;

import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;

public final class TransactionStatusRules {

	private TransactionStatusRules() {
	}

	public static void assertDraft(TransactionStatus status) {
		if (status == TransactionStatus.APPROVED) {
			throw new BusinessException(ErrorCodes.TRANSACTION_ALREADY_APPROVED, "Approved transactions cannot be edited or deleted");
		}
		if (status == TransactionStatus.VOID) {
			throw new BusinessException(ErrorCodes.TRANSACTION_ALREADY_VOID, "Voided transactions cannot be edited or deleted");
		}
		if (status != TransactionStatus.DRAFT) {
			throw new BusinessException(ErrorCodes.INVALID_STATUS_TRANSITION, "Only draft transactions can be modified or deleted");
		}
	}

	public static void assertCanApprove(TransactionStatus status) {
		if (status == TransactionStatus.APPROVED) {
			throw new BusinessException(ErrorCodes.TRANSACTION_ALREADY_APPROVED, "Transaction is already approved");
		}
		if (status == TransactionStatus.VOID) {
			throw new BusinessException(ErrorCodes.TRANSACTION_ALREADY_VOID, "Voided transactions cannot be approved");
		}
		if (status != TransactionStatus.DRAFT) {
			throw new BusinessException(ErrorCodes.INVALID_STATUS_TRANSITION, "Only DRAFT transactions can be approved");
		}
	}

	public static void assertCanVoid(TransactionStatus status) {
		if (status == TransactionStatus.VOID) {
			throw new BusinessException(ErrorCodes.TRANSACTION_ALREADY_VOID, "Transaction is already voided");
		}
		if (status == TransactionStatus.DRAFT) {
			throw new BusinessException(ErrorCodes.INVALID_STATUS_TRANSITION, "Draft transactions cannot be voided; delete the draft instead");
		}
		if (status != TransactionStatus.APPROVED) {
			throw new BusinessException(ErrorCodes.INVALID_STATUS_TRANSITION, "Only APPROVED transactions can be voided");
		}
	}

	public static boolean isFinalized(TransactionStatus status) {
		return status == TransactionStatus.APPROVED || status == TransactionStatus.VOID;
	}
}
