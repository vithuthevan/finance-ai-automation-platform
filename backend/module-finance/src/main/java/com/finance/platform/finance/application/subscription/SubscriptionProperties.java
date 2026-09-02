package com.finance.platform.finance.application.subscription;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.subscription")
@Getter
@Setter
public class SubscriptionProperties {

	private String defaultPlan = "STARTER";
	private int defaultTrialDays = 14;
}
