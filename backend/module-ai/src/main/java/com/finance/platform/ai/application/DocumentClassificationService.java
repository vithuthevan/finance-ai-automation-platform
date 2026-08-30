package com.finance.platform.ai.application;

import java.util.Optional;

public interface DocumentClassificationService {

	Optional<String> classify(ExtractedDocument extracted, String fileName);
}
