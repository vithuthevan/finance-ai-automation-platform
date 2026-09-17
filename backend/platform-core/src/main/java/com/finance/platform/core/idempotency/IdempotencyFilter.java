package com.finance.platform.core.idempotency;

import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.security.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
		if (rawKey == null || rawKey.isBlank()) {
			writeProblem(response, HttpServletResponse.SC_BAD_REQUEST, ErrorCodes.IDEMPOTENCY_KEY_REQUIRED,
					"Idempotency-Key header is required for this operation");
			return;
		}

		String contentType = request.getContentType() == null ? "" : request.getContentType();
		HttpServletRequest downstream = request;
		String bodyHash;
		if (contentType.toLowerCase().startsWith("multipart/")) {
			// Avoid buffering large bank CSV uploads twice; scope key with size + type.
			bodyHash = IdempotencyService.sha256("multipart:" + contentType + ":" + request.getContentLengthLong());
		} else {
			RepeatableBodyRequest repeatable = RepeatableBodyRequest.from(request);
			downstream = repeatable;
			bodyHash = IdempotencyService.sha256(repeatable.body());
		}
		String requestHash = IdempotencyService.sha256(
				downstream.getMethod() + " " + downstream.getRequestURI() + " " + bodyHash);

		Optional<IdempotencyKey> existing;
		try {
			existing = idempotencyService.begin(rawKey, downstream.getMethod(), downstream.getRequestURI(), requestHash);
		} catch (BusinessException ex) {
			writeProblem(response, HttpServletResponse.SC_CONFLICT, ex.getErrorCode(), ex.getMessage());
			return;
		} catch (DataIntegrityViolationException ex) {
			existing = idempotencyService.findExisting(rawKey);
			if (existing.isEmpty()) {
				writeProblem(response, HttpServletResponse.SC_CONFLICT, ErrorCodes.IDEMPOTENCY_CONFLICT,
						"A request with this Idempotency-Key is already in progress");
				return;
			}
		}
		if (existing.isPresent()) {
			IdempotencyKey row = existing.get();
			if (!row.getRequestHash().equals(requestHash)) {
				writeProblem(response, HttpServletResponse.SC_CONFLICT, ErrorCodes.IDEMPOTENCY_CONFLICT,
						"Idempotency-Key was reused with a different request");
				return;
			}
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
			filterChain.doFilter(downstream, wrapped);
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
		String json = "{\"title\":\"" + (status == 400 ? "Bad Request" : "Conflict")
				+ "\",\"detail\":\"" + escape(detail) + "\",\"errorCode\":\"" + errorCode + "\"}";
		response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
	}

	private static String escape(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
