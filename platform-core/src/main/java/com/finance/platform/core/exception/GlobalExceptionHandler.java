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
		return detail;
	}

	@ExceptionHandler(ValidationException.class)
	public ProblemDetail handleValidation(ValidationException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		detail.setTitle("Validation Failed");
		detail.setDetail(ex.getMessage());

		if (!ex.getFieldErrors().isEmpty()) {
			detail.setProperty("errors", ex.getFieldErrors());
		}

		return detail;
	}

	@ExceptionHandler(DuplicateResourceException.class)
	public ProblemDetail handleDuplicate(DuplicateResourceException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
		detail.setTitle("Duplicate Resource");
		detail.setDetail(ex.getMessage());
		return detail;
	}

	@ExceptionHandler(BusinessException.class)
	public ProblemDetail handleBusiness(BusinessException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		detail.setTitle("Business Rule Violation");
		detail.setDetail(ex.getMessage());
		return detail;
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
		detail.setTitle("Forbidden");
		detail.setDetail("You do not have permission to perform this action.");
		return detail;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleDtoValidation(MethodArgumentNotValidException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		detail.setTitle("Validation Failed");
		detail.setDetail("One or more fields are invalid.");
		detail.setProperty("errors", collectFieldErrors(ex));
		return detail;
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		if (isUniqueViolation(ex)) {
			ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
			detail.setTitle("Duplicate Resource");
			detail.setDetail("A resource with the same unique identifier already exists.");
			return detail;
		}

		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		detail.setTitle("Data Integrity Violation");
		detail.setDetail("The operation could not be completed due to a data constraint.");
		return detail;
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);

		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		detail.setTitle("Internal Server Error");
		detail.setDetail("An unexpected error occurred.");
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
		Throwable cause = ex.getMostSpecificCause();
		if (cause == null || cause.getMessage() == null) {
			return false;
		}

		String message = cause.getMessage().toLowerCase();
		return message.contains("unique") || message.contains("duplicate");
	}
}
