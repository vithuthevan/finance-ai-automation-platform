package com.finance.platform.finance.application.workflow;

import com.finance.platform.finance.domain.model.DocumentRequest;
import com.finance.platform.core.observability.ScheduledJobLogging;
import com.finance.platform.finance.infrastructure.persistence.DocumentRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OverdueDocumentRequestScheduler {

	private final DocumentRequestJpaRepository requestRepository;
	private final WorkflowNotificationService workflowNotificationService;

	@Scheduled(cron = "${app.workflow.overdue-reminder-cron:0 0 8 * * *}")
	@Transactional
	public void sendOverdueReminders() {
		ScheduledJobLogging.run("OVERDUE_DOCUMENT_REMINDERS", () -> {
			LocalDate today = LocalDate.now();
			List<DocumentRequest> overdue = requestRepository.findOverdueOpen(today);
			int sent = 0;
			for (DocumentRequest request : overdue) {
				try {
					request.getClient().getId();
					workflowNotificationService.documentRequestOverdue(request);
					sent++;
				} catch (Exception ex) {
					log.warn("Failed overdue reminder for request {}: {}", request.getId(), ex.getMessage());
				}
			}
			return sent;
		});
	}
}
