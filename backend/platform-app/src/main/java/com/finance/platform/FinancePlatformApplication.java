package com.finance.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.finance.platform")
@EntityScan(basePackages = "com.finance.platform")
@EnableJpaRepositories(basePackages = "com.finance.platform")
@EnableScheduling
public class FinancePlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinancePlatformApplication.class, args);
	}
}
