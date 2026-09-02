package com.finance.platform.core.notification;

public final class NotificationType {

	public static final String DOCUMENT_UPLOADED = "DOCUMENT_UPLOADED";
	public static final String DOCUMENT_NEEDS_REVIEW = "DOCUMENT_NEEDS_REVIEW";
	public static final String DOCUMENT_PROCESSING_FAILED = "DOCUMENT_PROCESSING_FAILED";
	public static final String TRANSACTION_AWAITING_APPROVAL = "TRANSACTION_AWAITING_APPROVAL";
	public static final String DOCUMENT_REQUEST_CREATED = "DOCUMENT_REQUEST_CREATED";
	public static final String DOCUMENT_REQUEST_UPLOADED = "DOCUMENT_REQUEST_UPLOADED";
	public static final String DOCUMENT_REQUEST_OVERDUE = "DOCUMENT_REQUEST_OVERDUE";
	public static final String BANK_IMPORT_COMPLETED = "BANK_IMPORT_COMPLETED";
	public static final String BANK_RECONCILIATION_REQUIRED = "BANK_RECONCILIATION_REQUIRED";
	public static final String PERIOD_READY_TO_CLOSE = "PERIOD_READY_TO_CLOSE";
	public static final String PERIOD_CLOSED = "PERIOD_CLOSED";
	public static final String SUBSCRIPTION_USAGE_WARNING = "SUBSCRIPTION_USAGE_WARNING";
	public static final String SUBSCRIPTION_SUSPENDED = "SUBSCRIPTION_SUSPENDED";
	public static final String TRIAL_ENDING = "TRIAL_ENDING";

	private NotificationType() {
	}
}
