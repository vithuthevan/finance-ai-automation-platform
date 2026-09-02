package com.finance.platform;

import com.finance.platform.support.TestReferenceDataConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestReferenceDataConfig.class)
class FinancePlatformApplicationTests {

	@Test
	void contextLoads() {
	}
}
