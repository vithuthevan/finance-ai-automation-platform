package com.finance.platform.controller.finance;

import com.finance.platform.finance.application.dto.PracticeTodayResponse;
import com.finance.platform.finance.application.service.PracticeTodayService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/practice")
@RequiredArgsConstructor
public class PracticeTodayController {

	private final PracticeTodayService practiceTodayService;

	@GetMapping("/today")
	@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
	public PracticeTodayResponse today() {
		return practiceTodayService.today();
	}
}
