package com.finance.platform.core.exception;

import java.util.UUID;

public class DuplicateDocumentException extends DuplicateResourceException {

	private final UUID existingDocumentId;

	public DuplicateDocumentException(UUID existingDocumentId, String checksum) {
		super(ErrorCodes.POSSIBLE_DUPLICATE, "Document", "checksum", checksum);
		this.existingDocumentId = existingDocumentId;
	}

	public UUID getExistingDocumentId() {
		return existingDocumentId;
	}
}
