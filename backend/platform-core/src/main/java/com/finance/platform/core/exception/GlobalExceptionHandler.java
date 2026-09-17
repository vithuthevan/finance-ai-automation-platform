package com.finance.platform.core.exception;

import com.finance.platform.core.observability.ObservabilityMdc;
import com.finance.platform.core.observability.SecurityEventLogger;
import com.finance.platform.core.observability.StructuredLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

	private final SecurityEventLogger securityEventLogger;

	@ExceptionHandler(ResourceNotFoundException.class)
	public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		detail.setTitle("Resource Not Found");
		detail.setDetail(ex.getMessage());
		detail.setProperty("errorCode", ex.getErrorCode());
		return detail;
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.PAYLOAD_TOO_LARGE);
		detail.setTitle("File Too Large");
		detail.setDetail("File exceeds the maximum upload size");
		detail.setProperty("errorCode", ErrorCodes.FILE_TOO_LARGE);
		return detail;
	}

	@ExceptionHandler(ValidationException.class)
	public ProblemDetail handleValidation(ValidationException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		detail.setTitle("Validation Failed");
		detail.setDetail(ex.getMessage());
		detail.setProperty("errorCode", ex.getErrorCode());

		if (!ex.getFieldErrors().isEmpty()) {
			detail.setProperty("errors", ex.getFieldErrors());
		}

		return detail;
	}

	@ExceptionHandler(DuplicateDocumentException.class)
	public ProblemDetail handleDuplicateDocument(DuplicateDocumentException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
		detail.setTitle("Possible Duplicate Document");
		detail.setDetail(ex.getMessage());
		detail.setProperty("errorCode", ex.getErrorCode());
		detail.setProperty("existingDocumentId", ex.getExistingDocumentId());
		return detail;
	}

	@ExceptionHandler(DuplicateResourceException.class)
	public ProblemDetail handleDuplicate(DuplicateResourceException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
		detail.setTitle("Duplicate Resource");
		detail.setDetail(ex.getMessage());
		detail.setProperty("errorCode", ex.getErrorCode());
		return detail;
	}

	@ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockingFailureException.class})
	public ProblemDetail handleOptimisticLock(RuntimeException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
		detail.setTitle("Concurrent Modification");
		detail.setDetail("This record was changed by another request. Refresh and try again.");
		detail.setProperty("errorCode", ErrorCodes.CONCURRENT_MODIFICATION);
		return detail;
	}

	@ExceptionHandler(RateLimitedException.class)
	public ProblemDetail handleRateLimited(RateLimitedException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
		detail.setTitle("Too Many Requests");
		detail.setDetail(ex.getMessage());
		detail.setProperty("errorCode", ex.getErrorCode());
		attachReferenceId(detail);
		return detail;
	}

	@ExceptionHandler(BusinessException.class)
	public ProblemDetail handleBusiness(BusinessException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		detail.setTitle("Business Rule Violation");
		detail.setDetail(ex.getMessage());
		detail.setProperty("errorCode", ex.getErrorCode());
		ex.getProperties().forEach(detail::setProperty);
		return detail;
	}

	@ExceptionHandler(IllegalStateException.class)
	public ProblemDetail handleIllegalState(IllegalStateException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		detail.setTitle("Business Rule Violation");
		detail.setDetail(ex.getMessage());
		detail.setProperty("errorCode", ErrorCodes.INVALID_STATUS_TRANSITION);
		return detail;
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
		securityEventLogger.permissionDenied(currentPath(), currentMethod());
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
		detail.setTitle("Forbidden");
		detail.setDetail(ex.getMessage() != null && !ex.getMessage().isBlank()
				? ex.getMessage()
				: "You do not have permission to perform this action.");
		detail.setProperty("errorCode", reportAccessDenied(ex) ? ErrorCodes.REPORT_ACCESS_DENIED : ErrorCodes.ACCESS_DENIED);
		attachReferenceId(detail);
		return detail;
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ProblemDetail handleUnreadableMessage(HttpMessageNotReadableException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		detail.setTitle("Validation Failed");
		detail.setDetail("Request body is malformed or contains invalid values.");
		detail.setProperty("errorCode", ErrorCodes.VALIDATION_FAILED);
		return detail;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleDtoValidation(MethodArgumentNotValidException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		detail.setTitle("Validation Failed");
		detail.setDetail("One or more fields are invalid.");
		detail.setProperty("errorCode", ErrorCodes.VALIDATION_FAILED);
		detail.setProperty("errors", collectFieldErrors(ex));
		return detail;
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		log.warn("Data integrity violation [requestId={}]: {}", ObservabilityMdc.currentRequestId(), causeMessage(ex));
		if (isUniqueViolation(ex)) {
			ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
			detail.setTitle("Duplicate Resource");
			detail.setDetail("A resource with the same unique identifier already exists.");
			String message = causeMessage(ex);
			detail.setProperty("errorCode", message.contains("uq_categories_")
					? ErrorCodes.DUPLICATE_CATEGORY_CODE
					: ErrorCodes.DUPLICATE_RESOURCE);
			return detail;
		}

		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		detail.setTitle("Data Integrity Violation");
		detail.setDetail("The operation could not be completed due to a data constraint.");
		detail.setProperty("errorCode", ErrorCodes.BUSINESS_RULE_VIOLATION);
		return detail;
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ProblemDetail handleNoResource(NoResourceFoundException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		detail.setTitle("Resource Not Found");
		detail.setDetail("The requested API path was not found.");
		detail.setProperty("errorCode", "RESOURCE_NOT_FOUND");
		return detail;
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) {
		StructuredLog.error(log, "UNHANDLED_EXCEPTION",
				StructuredLog.baseFields("ERROR"), ex);

		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		detail.setTitle("Internal Server Error");
		detail.setDetail("Something went wrong. Reference ID: " + ObservabilityMdc.currentRequestId());
		detail.setProperty("errorCode", "INTERNAL_ERROR");
		attachReferenceId(detail);
		return detail;
	}

	private void attachReferenceId(ProblemDetail detail) {
		detail.setProperty("referenceId", ObservabilityMdc.currentRequestId());
	}

	private String currentPath() {
		var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
		if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttributes) {
			return servletAttributes.getRequest().getRequestURI();
		}
		return "unknown";
	}

	private String currentMethod() {
		var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
		if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttributes) {
			return servletAttributes.getRequest().getMethod();
		}
		return "unknown";
	}

	private boolean reportAccessDenied(AccessDeniedException ex) {
		String message = ex.getMessage();
		return message != null && message.toLowerCase().contains("financial report");
	}

	private Map<String, String> collectFieldErrors(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return errors;
	}

	private boolean isUniqueViolation(DataIntegrityViolationException ex) {
		String message = causeMessage(ex).toLowerCase();
		return message.contains("unique") || message.contains("duplicate");
	}

	private String causeMessage(DataIntegrityViolationException ex) {
		Throwable cause = ex.getMostSpecificCause();
		return cause != null && cause.getMessage() != null ? cause.getMessage() : "";
	}
}
