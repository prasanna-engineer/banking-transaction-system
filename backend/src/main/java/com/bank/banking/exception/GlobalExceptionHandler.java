package com.bank.banking.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into JSON error responses (RFC 9457 "problem detail" format).
 * Extending ResponseEntityExceptionHandler covers Spring's own errors too
 * (malformed JSON, unknown URL, wrong HTTP method, ...).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ResourceNotFoundException.class)
	public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
	}

	@ExceptionHandler(BusinessException.class)
	public ProblemDetail handleBusiness(BusinessException ex) {
		return problem(HttpStatusCode.valueOf(422), "Business Rule Violation", ex.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
			.forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

		ProblemDetail body = ex.getBody();
		body.setTitle("Validation Failed");
		body.setDetail("One or more fields are invalid");
		body.setProperty("errors", errors);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	private ProblemDetail problem(HttpStatusCode status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

}
