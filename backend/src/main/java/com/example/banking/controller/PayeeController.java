package com.example.banking.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.banking.dto.payee.CreatePayeeRequest;
import com.example.banking.dto.payee.PayeeResponse;
import com.example.banking.security.CurrentUserId;
import com.example.banking.service.PayeeService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

@Tag(name = "Payees", description = "Saved recipients for transfers")
@RestController
@RequestMapping("/api/v1/payees")
public class PayeeController {

	private final PayeeService payeeService;

	public PayeeController(PayeeService payeeService) {
		this.payeeService = payeeService;
	}

	@GetMapping
	public List<PayeeResponse> list(@CurrentUserId Long currentUserId) {
		return payeeService.list(currentUserId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PayeeResponse create(@CurrentUserId Long currentUserId, @Valid @RequestBody CreatePayeeRequest request) {
		return payeeService.create(currentUserId, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@CurrentUserId Long currentUserId, @PathVariable Long id) {
		payeeService.delete(currentUserId, id);
	}

}
