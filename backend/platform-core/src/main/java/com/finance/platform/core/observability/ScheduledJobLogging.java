package com.finance.platform.core.observability;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntSupplier;

@Slf4j
public final class ScheduledJobLogging {

	private ScheduledJobLogging() {
	}

	public static void run(String jobName, IntSupplier work) {
		String jobId = ObservabilityMdc.generateRequestId();
		ObservabilityMdc.setRequestId(jobId);
		ObservabilityMdc.setModule("scheduler");
		ObservabilityMdc.setOperation(jobName);
		long started = System.currentTimeMillis();
		Map<String, Object> startFields = StructuredLog.baseFields("STARTED");
		startFields.put("jobId", jobId);
		StructuredLog.info(log, jobName, startFields);
		try {
			int processed = work.getAsInt();
			Map<String, Object> successFields = StructuredLog.baseFields("SUCCESS");
			successFields.put("jobId", jobId);
			successFields.put("processed", processed);
			successFields.put("durationMs", System.currentTimeMillis() - started);
			StructuredLog.info(log, jobName, successFields);
		} catch (RuntimeException ex) {
			Map<String, Object> failureFields = StructuredLog.baseFields("FAILURE");
			failureFields.put("jobId", jobId);
			failureFields.put("durationMs", System.currentTimeMillis() - started);
			StructuredLog.error(log, jobName, failureFields, ex);
			throw ex;
		} finally {
			ObservabilityMdc.clear();
		}
	}
}
