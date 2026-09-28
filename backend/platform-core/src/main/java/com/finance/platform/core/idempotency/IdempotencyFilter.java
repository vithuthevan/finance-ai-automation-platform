package com.finance.platform.core.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

	public static final String HEADER = "Idempotency-Key";

	private static final Pattern PROTECTED = Pattern.compile(
			"^/api/v1/clients/[^/]+/(expenses(/[^/]+/(approve|void))?|income(/[^/]+/(approve|void))?|"
					+ "bank/(imports|transactions/[^/]+/(confirm|create-expense|create-income))|documents/[^/]+/review/accept|"
					+ "periods/[^/]+/close)$"
	);

	private final IdempotencyService idempotencyService;
	private final ObjectMapper objectMapper;

	@Override
	protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
		if (!"POST".equalsIgnoreCase(request.getMethod())) {
			return true;
		}
		if (TenantContextHolder.get() == null) {
			return true;
		}
		String path = request.getRequestURI();
		return path == null || !PROTECTED.matcher(path).matches();
	}

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain
	) throws ServletException, IOException {
		String rawKey = request.getHeader(HEADER);
		try {
			IdempotencyFingerprint.validateKey(rawKey);
		} catch (BusinessException ex) {
			writeProblem(response, statusFor(ex), ex.getErrorCode(), ex.getMessage());
			return;
		}

		HttpServletRequest downstream;
		String bodyHash;
		try {
			RepeatableBodyRequest repeatable = RepeatableBodyRequest.from(request);
			downstream = repeatable;
			bodyHash = IdempotencyService.sha256(repeatable.body());
		} catch (IdempotencyPayloadTooLargeException ex) {
			writeProblem(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, ErrorCodes.FILE_TOO_LARGE,
					"Request body exceeds the maximum size");
			return;
		}

		String path = downstream.getRequestURI();
		String requestHash = IdempotencyService.sha256(IdempotencyFingerprint.requestIdentity(
				downstream.getMethod(), path, IdempotencyFingerprint.canonicalQuery(downstream.getQueryString()), bodyHash));

		IdempotencyBeginResult outcome;
		try {
			outcome = idempotencyService.begin(rawKey, downstream.getMethod(), path, requestHash);
		} catch (BusinessException ex) {
			writeProblem(response, statusFor(ex), ex.getErrorCode(), ex.getMessage());
			return;
		}

		if (outcome.kind() == IdempotencyBeginResult.Kind.REPLAY) {
			replay(response, outcome.statusCode() == null ? HttpServletResponse.SC_OK : outcome.statusCode(), outcome.responseBody());
			return;
		}
		if (outcome.kind() == IdempotencyBeginResult.Kind.IN_PROGRESS) {
			writeProblem(response, HttpServletResponse.SC_CONFLICT, ErrorCodes.IDEMPOTENCY_CONFLICT,
					"A request with this Idempotency-Key is already in progress");
			return;
		}

		ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
		boolean settled = false;
		try {
			filterChain.doFilter(downstream, wrapped);
			int status = wrapped.getStatus();
			if (status < 200 || status >= 500) {
				idempotencyService.release(outcome.claimId());
			} else {
				String body = new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8);
				idempotencyService.complete(outcome.claimId(), status, body);
			}
			settled = true;
		} finally {
			if (!settled) {
				idempotencyService.release(outcome.claimId());
			}
			wrapped.copyBodyToResponse();
		}
	}

	private static int statusFor(BusinessException ex) {
		String code = ex.getErrorCode();
		if (ErrorCodes.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST.equals(code)
				|| ErrorCodes.IDEMPOTENCY_CONFLICT.equals(code)) {
			return HttpServletResponse.SC_CONFLICT;
		}
		return HttpServletResponse.SC_BAD_REQUEST;
	}

	private static void replay(HttpServletResponse response, int status, String body) throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		if (body != null && !body.isEmpty()) {
			response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
		}
	}

	private void writeProblem(HttpServletResponse response, int status, String errorCode, String detail) throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		Map<String, Object> problem = new LinkedHashMap<>();
		problem.put("title", titleFor(status));
		problem.put("status", status);
		problem.put("detail", detail);
		problem.put("errorCode", errorCode);
		response.getOutputStream().write(objectMapper.writeValueAsBytes(problem));
	}

	private static String titleFor(int status) {
		return switch (status) {
			case HttpServletResponse.SC_BAD_REQUEST -> "Bad Request";
			case HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE -> "Payload Too Large";
			default -> "Conflict";
		};
	}
}
