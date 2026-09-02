package com.finance.platform.core.subscription;

import java.time.LocalDate;
import java.util.UUID;

public interface SubscriptionQuotaGuard {

	void assertCanWrite(UUID firmId);

	void assertCanCreateActiveClient(UUID firmId);

	void assertCanCreateActiveUser(UUID firmId);

	void assertCanUploadDocument(UUID firmId, long incomingFileBytes);

	boolean canProcessAi(UUID firmId);

	String aiQuotaMessage(UUID firmId);
}
