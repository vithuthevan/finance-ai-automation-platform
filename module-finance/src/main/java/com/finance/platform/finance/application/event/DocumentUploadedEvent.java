package com.finance.platform.finance.application.event;

import java.util.UUID;

public record DocumentUploadedEvent(UUID documentId, UUID clientId, UUID firmId) {
}
