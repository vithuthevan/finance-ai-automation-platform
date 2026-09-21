package com.finance.platform.core.outbox;

public interface OutboxEventHandler {

	boolean supports(String eventType);

	void handle(EventOutbox row) throws Exception;
}
