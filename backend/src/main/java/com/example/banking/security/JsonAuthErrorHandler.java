package com.example.banking.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.example.banking.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Security rejects requests in a filter, before any controller runs, so @RestControllerAdvice
 * never sees them. This writes the same ErrorResponse JSON for 401 and 403.
 */
@Component
public class JsonAuthErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final JsonMapper jsonMapper;

	public JsonAuthErrorHandler(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	// 401: no token, or a token that is expired, tampered with, or signed by someone else.
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		response.setHeader("WWW-Authenticate", "Bearer");
		if (exception instanceof InvalidBearerTokenException) {
			write(response, request, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
					"The access token is invalid or has expired.");
		}
		else {
			write(response, request, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.");
		}
	}

	// 403: valid token, but the role is not allowed here (e.g. a customer calling an admin endpoint).
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
			throws IOException {
		write(response, request, HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action.");
	}

	private void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String code,
			String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		jsonMapper.writeValue(response.getOutputStream(), ErrorResponse.of(status, code, message, request.getRequestURI()));
	}

}
