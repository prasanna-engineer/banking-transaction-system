package com.example.banking.dto;

import java.time.Instant;
import java.util.List;

/** The single JSON error shape used by every endpoint (project guide, section 6). */
public record ErrorResponse(Instant timestamp, int status, String code, String message, String path,
		List<FieldError> fieldErrors) {

	public record FieldError(String field, String message) {
	}

}
