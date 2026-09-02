package com.finance.platform.finance.application.event;

import java.util.UUID;

public record PeriodReadyToCloseEvent(UUID firmId, UUID clientId, UUID periodId) {
}
