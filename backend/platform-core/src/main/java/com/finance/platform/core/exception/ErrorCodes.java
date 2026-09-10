package com.finance.platform.core.exception;

public final class ErrorCodes {

	public static final String BUSINESS_RULE_VIOLATION = "BUSINESS_RULE_VIOLATION";
	public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
	public static final String DUPLICATE_RESOURCE = "DUPLICATE_RESOURCE";
	public static final String ACCESS_DENIED = "ACCESS_DENIED";
	public static final String INVALID_STATUS_TRANSITION = "INVALID_STATUS_TRANSITION";
	public static final String INVALID_CATEGORY = "INVALID_CATEGORY";
	public static final String CLIENT_INACTIVE = "CLIENT_INACTIVE";
	public static final String CATEGORY_INACTIVE = "CATEGORY_INACTIVE";
	public static final String DUPLICATE_CATEGORY_CODE = "DUPLICATE_CATEGORY_CODE";
	public static final String TRANSACTION_ALREADY_APPROVED = "TRANSACTION_ALREADY_APPROVED";
	public static final String TRANSACTION_ALREADY_VOID = "TRANSACTION_ALREADY_VOID";
	public static final String USER_INACTIVE = "USER_INACTIVE";
	public static final String PERIOD_CLOSED = "PERIOD_CLOSED";
	public static final String PERIOD_NOT_READY = "PERIOD_NOT_READY";
	public static final String PERIOD_NOT_READY_TO_CLOSE = "PERIOD_NOT_READY_TO_CLOSE";
	public static final String PERIOD_ALREADY_CLOSED = "PERIOD_ALREADY_CLOSED";
	public static final String PERIOD_NOT_CLOSED = "PERIOD_NOT_CLOSED";
	public static final String PERIOD_REOPEN_REASON_REQUIRED = "PERIOD_REOPEN_REASON_REQUIRED";
	public static final String ACCOUNTING_PERIOD_OVERLAP = "ACCOUNTING_PERIOD_OVERLAP";
	public static final String DOCUMENT_REQUEST_ALREADY_COMPLETED = "DOCUMENT_REQUEST_ALREADY_COMPLETED";
	public static final String DUPLICATE_DOCUMENT = "DUPLICATE_DOCUMENT";
	public static final String POSSIBLE_DUPLICATE = "POSSIBLE_DUPLICATE";
	public static final String DOCUMENT_NOT_FOUND = "DOCUMENT_NOT_FOUND";
	public static final String DOCUMENT_STORAGE_FAILED = "DOCUMENT_STORAGE_FAILED";
	public static final String DOCUMENT_OBJECT_MISSING = "DOCUMENT_OBJECT_MISSING";
	public static final String DOCUMENT_ACCESS_DENIED = "DOCUMENT_ACCESS_DENIED";
	public static final String INVALID_DOCUMENT_TYPE = "INVALID_DOCUMENT_TYPE";
	public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
	public static final String UNSUPPORTED_FILE_TYPE = "UNSUPPORTED_FILE_TYPE";
	public static final String DOCUMENT_ALREADY_LINKED = "DOCUMENT_ALREADY_LINKED";
	public static final String DOCUMENT_CLIENT_MISMATCH = "DOCUMENT_CLIENT_MISMATCH";
	public static final String RECONCILIATION_CONFLICT = "RECONCILIATION_CONFLICT";
	public static final String BANK_ACCOUNT_NOT_FOUND = "BANK_ACCOUNT_NOT_FOUND";
	public static final String BANK_IMPORT_NOT_FOUND = "BANK_IMPORT_NOT_FOUND";
	public static final String BANK_IMPORT_INVALID = "BANK_IMPORT_INVALID";
	public static final String BANK_IMPORT_DUPLICATE = "BANK_IMPORT_DUPLICATE";
	public static final String BANK_IMPORT_MAPPING_INVALID = "BANK_IMPORT_MAPPING_INVALID";
	public static final String BANK_TRANSACTION_NOT_FOUND = "BANK_TRANSACTION_NOT_FOUND";
	public static final String BANK_TRANSACTION_ALREADY_MATCHED = "BANK_TRANSACTION_ALREADY_MATCHED";
	public static final String BANK_TRANSACTION_CLIENT_MISMATCH = "BANK_TRANSACTION_CLIENT_MISMATCH";
	public static final String INVALID_RECONCILIATION_DIRECTION = "INVALID_RECONCILIATION_DIRECTION";
	public static final String RECONCILIATION_ALREADY_CONFIRMED = "RECONCILIATION_ALREADY_CONFIRMED";
	public static final String REPORT_CLIENT_NOT_FOUND = "REPORT_CLIENT_NOT_FOUND";
	public static final String INVALID_REPORT_PERIOD = "INVALID_REPORT_PERIOD";
	public static final String REPORT_ACCESS_DENIED = "REPORT_ACCESS_DENIED";
	public static final String REPORT_EXPORT_FAILED = "REPORT_EXPORT_FAILED";
	public static final String REPORT_NO_DATA = "REPORT_NO_DATA";
	public static final String AI_DISABLED = "AI_DISABLED";
	public static final String DOCUMENT_PROCESSING_FAILED = "DOCUMENT_PROCESSING_FAILED";
	public static final String DOCUMENT_ALREADY_PROCESSING = "DOCUMENT_ALREADY_PROCESSING";
	public static final String EXTRACTION_NOT_AVAILABLE = "EXTRACTION_NOT_AVAILABLE";
	public static final String INVALID_AI_SUGGESTION = "INVALID_AI_SUGGESTION";
	public static final String AI_PROVIDER_UNAVAILABLE = "AI_PROVIDER_UNAVAILABLE";
	public static final String AI_PROVIDER_TIMEOUT = "AI_PROVIDER_TIMEOUT";
	public static final String AI_RATE_LIMITED = "AI_RATE_LIMITED";
	public static final String AI_REVIEW_REQUIRED = "AI_REVIEW_REQUIRED";
	public static final String REMINDER_TOO_SOON = "REMINDER_TOO_SOON";
	public static final String INVALID_CLIENT_ASSIGNMENT = "INVALID_CLIENT_ASSIGNMENT";
	public static final String EMAIL_DELIVERY_FAILED = "EMAIL_DELIVERY_FAILED";
	public static final String PLAN_CLIENT_LIMIT_REACHED = "PLAN_CLIENT_LIMIT_REACHED";
	public static final String PLAN_USER_LIMIT_REACHED = "PLAN_USER_LIMIT_REACHED";
	public static final String PLAN_DOCUMENT_LIMIT_REACHED = "PLAN_DOCUMENT_LIMIT_REACHED";
	public static final String PLAN_AI_LIMIT_REACHED = "PLAN_AI_LIMIT_REACHED";
	public static final String PLAN_STORAGE_LIMIT_REACHED = "PLAN_STORAGE_LIMIT_REACHED";
	public static final String SUBSCRIPTION_NOT_FOUND = "SUBSCRIPTION_NOT_FOUND";
	public static final String SUBSCRIPTION_INACTIVE = "SUBSCRIPTION_INACTIVE";
	public static final String SUBSCRIPTION_SUSPENDED = "SUBSCRIPTION_SUSPENDED";
	public static final String PLAN_NOT_FOUND = "PLAN_NOT_FOUND";
	public static final String INVALID_TIMEZONE = "INVALID_TIMEZONE";
	public static final String INVALID_CURRENCY = "INVALID_CURRENCY";
	public static final String UPLOADS_UNAVAILABLE = "UPLOADS_UNAVAILABLE";
	public static final String RATE_LIMITED = "RATE_LIMITED";
	public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";
	public static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";

	private ErrorCodes() {
	}

	public static String notFound(String resource) {
		if (resource == null || resource.isBlank()) {
			return "RESOURCE_NOT_FOUND";
		}
		return resource.trim().toUpperCase().replace(' ', '_') + "_NOT_FOUND";
	}
}
