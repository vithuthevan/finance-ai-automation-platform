package com.finance.platform.ai.application;

import java.util.Optional;

public interface DocumentExtractionService {

	boolean isEnabled();

	Optional<ExtractedDocument> extract(byte[] content, String fileName, String mimeType);
}
