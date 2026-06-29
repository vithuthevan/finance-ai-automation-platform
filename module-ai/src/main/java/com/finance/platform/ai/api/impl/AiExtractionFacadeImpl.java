package com.finance.platform.ai.api.impl;

import com.finance.platform.ai.api.AiExtractionFacade;
import com.finance.platform.core.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AiExtractionFacadeImpl implements AiExtractionFacade {

	@Override
	public UUID triggerExtraction(UUID receiptId) {
		throw new BusinessException("AI extraction module not yet implemented");
	}

	@Override
	public ExtractionJobStatus getJobStatus(UUID jobId) {
		throw new BusinessException("AI extraction module not yet implemented");
	}
}
