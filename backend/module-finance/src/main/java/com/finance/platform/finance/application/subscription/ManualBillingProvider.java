package com.finance.platform.finance.application.subscription;

import org.springframework.stereotype.Service;

@Service
public class ManualBillingProvider implements BillingProvider {

	@Override
	public String providerName() {
		return "manual";
	}
}
