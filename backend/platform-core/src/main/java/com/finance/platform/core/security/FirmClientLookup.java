package com.finance.platform.core.security;

import java.util.UUID;

/**
 * Firm-scoped client existence check for modules that cannot depend on module-finance.
 */
public interface FirmClientLookup {

	boolean existsByIdAndFirmId(UUID clientId, UUID firmId);

	boolean existsActiveByIdAndFirmId(UUID clientId, UUID firmId);
}
