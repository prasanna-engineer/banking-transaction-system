package com.bank.banking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bank.banking.dto.TransferRequest;
import com.bank.banking.dto.TransferResponse;
import com.bank.banking.service.TransactionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

	private final TransactionService transactionService;

	public TransferController(TransactionService transactionService) {
		this.transactionService = transactionService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
		return transactionService.transfer(request);
	}

}
