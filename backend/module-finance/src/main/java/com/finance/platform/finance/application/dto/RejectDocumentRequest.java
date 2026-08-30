package com.finance.platform.finance.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectDocumentRequest(
		@NotBlank @Size(max = 2000) String reason
) {
}
