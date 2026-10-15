package com.example.banking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.banking.dto.ErrorResponse;
import com.example.banking.dto.transfer.TransferRequest;
import com.example.banking.dto.transfer.TransferResponse;
import com.example.banking.security.CurrentUserId;
import com.example.banking.service.TransferService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

@Tag(name = "Transfers", description = "Move money between accounts (customers only)")
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

	private final TransferService transferService;

	public TransferController(TransferService transferService) {
		this.transferService = transferService;
	}

	@Operation(summary = "Transfer money", description = """
			Moves money from one of your accounts to another active account in the same currency.
			Give the destination as `destinationAccountId` (your own account) or `destinationAccountNumber`
			(anyone's), not both. Send `amount` as a string with at most 2 decimals, e.g. `"250.75"`.
			Debit, credit, transaction record and audit entry are committed together or not at all.
			""")
	@ApiResponse(responseCode = "201", description = "Transfer completed")
	@ApiResponse(responseCode = "400", description = "VALIDATION_FAILED, SAME_ACCOUNT, INSUFFICIENT_FUNDS, "
			+ "CURRENCY_MISMATCH or AMOUNT_LIMIT_EXCEEDED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "403", description = "Only customers can transfer", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND (also for accounts that are not yours)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "ACCOUNT_NOT_ACTIVE or CONCURRENT_UPDATE", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TransferResponse transfer(@CurrentUserId Long currentUserId, @Valid @RequestBody TransferRequest request) {
		return transferService.transfer(currentUserId, request);
	}

}
