package com.finance.platform.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		detail.setTitle("Resource Not Found");
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
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		detail.setTitle("Validation Failed");
		detail.setDetail("One or more fields are invalid.");
		return detail;
	}
}
