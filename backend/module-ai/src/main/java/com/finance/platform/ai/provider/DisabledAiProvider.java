package com.finance.platform.ai.provider;

import com.finance.platform.ai.application.DocumentClassificationService;
import com.finance.platform.ai.application.DocumentExtractionService;
import com.finance.platform.ai.application.ExtractedDocument;
import com.finance.platform.ai.application.TransactionSuggestionService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DisabledAiProvider implements DocumentExtractionService, DocumentClassificationService, TransactionSuggestionService {

	@Override
	public boolean isEnabled() {
		return false;
	}

	@Override
	public Optional<ExtractedDocument> extract(byte[] content, String fileName, String mimeType) {
		return Optional.empty();
	}

	@Override
	public Optional<String> classify(ExtractedDocument extracted, String fileName) {
		return Optional.empty();
	}

	@Override
	public ExtractedDocument suggest(ExtractedDocument extracted) {
		return extracted;
	}
}
