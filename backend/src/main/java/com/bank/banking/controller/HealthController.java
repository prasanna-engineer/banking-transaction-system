package com.bank.banking.controller;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

	public record HealthResponse(String status, String service, Instant timestamp) {}

	@GetMapping("/health")
	public HealthResponse health() {
		return new HealthResponse("UP", "banking-system", Instant.now());
	}

}
