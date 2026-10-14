package com.example.banking.exception;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.example.banking.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Turns exceptions thrown anywhere in a controller call into the standard ErrorResponse JSON.
 * Like Laravel's exception Handler::render(), but chosen per exception type.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
		return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request);
	}

	// Temporary until Milestone 4: a missing X-Demo-User-Id header means "not logged in".
	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex,
			HttpServletRequest request) {
		ApiException unauthenticated = ApiException.unauthenticated();
		return build(unauthenticated.getStatus(), unauthenticated.getCode(), unauthenticated.getMessage(), request);
	}

	// e.g. /accounts/abc or ?type=SHOPPING
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Invalid value for parameter '" + ex.getName() + "'.",
				request);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
				"Required parameter '" + ex.getParameterName() + "' is missing.", request);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.", request);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
			HttpServletRequest request) {
		return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
				"HTTP method " + ex.getMethod() + " is not supported here.", request);
	}

	// Safety net: log the full details on the server, but tell the client nothing internal.
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.", request);
	}

	private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
			HttpServletRequest request) {
		ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), code, message, request.getRequestURI(),
				List.of());
		return ResponseEntity.status(status).body(body);
	}

}
