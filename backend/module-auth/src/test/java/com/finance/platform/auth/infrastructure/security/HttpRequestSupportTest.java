package com.finance.platform.auth.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestSupportTest {

	@Test
	void doesNotTrustClientSuppliedForwardedHeaderWhenRemoteAddrIsAuthoritative() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("203.0.113.10");
		request.addHeader("X-Forwarded-For", "198.51.100.1");
		assertThat(HttpRequestSupport.resolveClientIp(request)).isEqualTo("203.0.113.10");
	}
}
