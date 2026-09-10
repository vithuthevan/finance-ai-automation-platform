package com.finance.platform.core.idempotency;

import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.security.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

	public static final String HEADER = "Idempotency-Key";

	private static final Pattern PROTECTED = Pattern.compile(
			"^/api/v1/clients/[^/]+/(expenses(/[^/]+/(approve|void))?|income(/[^/]+/(approve|void))?|"
					+ "bank/(imports|transactions/[^/]+/confirm)|documents/[^/]+/review/accept|"
					+ "periods/[^/]+/close)$"
	);

	private final IdempotencyService idempotencyService;

	@Override
	protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
		if (!"POST".equalsIgnoreCase(request.getMethod())) {
			return true;
		}
		if (request.getHeader(HEADER) == null || request.getHeader(HEADER).isBlank()) {
			return true;
		}
		if (TenantContextHolder.get() == null) {
			return true;
		}
		String path = request.getRequestURI();
		return !PROTECTED.matcher(path).matches();
	}

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain
	) throws ServletException, IOException {
		String rawKey = request.getHeader(HEADER);
		String requestHash = IdempotencyService.sha256(request.getMethod() + " " + request.getRequestURI());
		Optional<IdempotencyKey> existing;
		try {
			existing = idempotencyService.begin(rawKey, request.getMethod(), request.getRequestURI(), requestHash);
		} catch (BusinessException ex) {
			writeProblem(response, HttpServletResponse.SC_CONFLICT, ex.getErrorCode(), ex.getMessage());
			return;
		}
		if (existing.isPresent()) {
			IdempotencyKey row = existing.get();
			if (row.getStatus() == IdempotencyKey.Status.COMPLETED && row.getStatusCode() != null) {
				response.setStatus(row.getStatusCode());
				response.setContentType(MediaType.APPLICATION_JSON_VALUE);
				if (row.getResponseBody() != null) {
					response.getOutputStream().write(row.getResponseBody().getBytes(StandardCharsets.UTF_8));
				}
				return;
			}
			writeProblem(response, HttpServletResponse.SC_CONFLICT, ErrorCodes.IDEMPOTENCY_CONFLICT,
					"A request with this Idempotency-Key is already in progress");
			return;
		}

		ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
		try {
			filterChain.doFilter(request, wrapped);
			if (wrapped.getStatus() < 500) {
				String body = new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8);
				idempotencyService.complete(rawKey, wrapped.getStatus(), body);
			}
		} finally {
			wrapped.copyBodyToResponse();
		}
	}

	private static void writeProblem(HttpServletResponse response, int status, String errorCode, String detail)
			throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		String json = "{\"title\":\"Conflict\",\"detail\":\"" + escape(detail) + "\",\"errorCode\":\"" + errorCode + "\"}";
		response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
	}

	private static String escape(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
