package com.finance.platform.finance.application.event;

import java.util.UUID;

public record DocumentProcessingFailedEvent(UUID documentId, UUID clientId, UUID firmId) {
}
