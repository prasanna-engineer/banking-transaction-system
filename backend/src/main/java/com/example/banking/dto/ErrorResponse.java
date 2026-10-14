package com.example.banking.dto;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;

/** The single JSON error shape used by every endpoint (project guide, section 6). */
public record ErrorResponse(Instant timestamp, int status, String code, String message, String path,
		List<FieldError> fieldErrors) {

	public static ErrorResponse of(HttpStatus status, String code, String message, String path) {
		return of(status, code, message, path, List.of());
	}

	public static ErrorResponse of(HttpStatus status, String code, String message, String path,
			List<FieldError> fieldErrors) {
		return new ErrorResponse(Instant.now(), status.value(), code, message, path, fieldErrors);
	}

	public record FieldError(String field, String message) {
	}

}
