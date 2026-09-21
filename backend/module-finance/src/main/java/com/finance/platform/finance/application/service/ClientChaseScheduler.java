package com.finance.platform.finance.application.service;

import com.finance.platform.finance.infrastructure.persistence.FirmJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientChaseScheduler {

	private final FirmJpaRepository firmRepository;
	private final ClientChaseService chaseService;

	@Scheduled(cron = "${app.chase.cron:0 0 8 * * *}")
	@Transactional
	public void dispatchDueChaseSteps() {
		firmRepository.findAll().forEach(firm -> {
			try {
				chaseService.executeDueChaseStepsForFirm(firm.getId());
			} catch (Exception ex) {
				log.warn("Client chase dispatch failed for firm {}: {}", firm.getId(), ex.getMessage());
			}
		});
	}
}
