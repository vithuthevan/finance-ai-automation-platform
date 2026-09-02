package com.finance.platform.security;

import com.finance.platform.support.AbstractPostgresIntegrationTest;
import com.finance.platform.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileUploadSecurityIntegrationTest extends AbstractPostgresIntegrationTest {

	private IntegrationTestSupport.Session admin;
	private String clientId;

	@BeforeEach
	void setUp() throws Exception {
		support.seedRolesIfNeeded();
		admin = support.registerFirmAdmin("Upload Security");
		var client = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Upload Client\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		clientId = support.read(client, com.finance.platform.finance.application.dto.ClientResponse.class).id().toString();
	}

	@Test
	void rejectsExecutableUpload() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "../../evil.exe", "application/octet-stream", new byte[]{1, 2, 3});
		mockMvc.perform(multipart("/api/v1/clients/" + clientId + "/documents").file(file)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isBadRequest());
	}

	@Test
	void acceptsValidPngUpload() throws Exception {
		byte[] png = new byte[]{
				(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
				0, 0, 0, 0, 0, 0, 0, 0
		};
		MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", png);
		mockMvc.perform(multipart("/api/v1/clients/" + clientId + "/documents").file(file)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isCreated());
	}
}
