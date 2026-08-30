package com.finance.platform.core.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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

	@ExceptionHandler(BusinessException.class)
	public ProblemDetail handleBusiness(BusinessException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		detail.setTitle("Business Rule Violation");
		detail.setDetail(ex.getMessage());
		detail.setProperty("errorCode", ex.getErrorCode());
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
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
		detail.setTitle("Forbidden");
		detail.setDetail(ex.getMessage() != null && !ex.getMessage().isBlank()
				? ex.getMessage()
				: "You do not have permission to perform this action.");
		detail.setProperty("errorCode", ErrorCodes.ACCESS_DENIED);
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

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);

		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		detail.setTitle("Internal Server Error");
		detail.setDetail("An unexpected error occurred.");
		detail.setProperty("errorCode", "INTERNAL_ERROR");
		return detail;
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
