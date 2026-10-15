package com.example.banking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.banking.dto.transfer.TransferRequest;
import com.example.banking.dto.transfer.TransferResponse;
import com.example.banking.security.CurrentUserId;
import com.example.banking.service.TransferService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

	private final TransferService transferService;

	public TransferController(TransferService transferService) {
		this.transferService = transferService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TransferResponse transfer(@CurrentUserId Long currentUserId, @Valid @RequestBody TransferRequest request) {
		return transferService.transfer(currentUserId, request);
	}

}
