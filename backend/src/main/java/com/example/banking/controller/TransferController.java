package com.example.banking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.banking.dto.ErrorResponse;
import com.example.banking.dto.transfer.TransferRequest;
import com.example.banking.dto.transfer.TransferResponse;
import com.example.banking.security.CurrentUserId;
import com.example.banking.service.OtpService;
import com.example.banking.service.TransferService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

@Tag(name = "Transfers", description = "Move money between accounts (customers only)")
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

	static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

	private final TransferService transferService;

	public TransferController(TransferService transferService) {
		this.transferService = transferService;
	}

	@Operation(summary = "Transfer money", description = """
			Moves money from one of your accounts to another active account in the same currency.
			Give the destination as `destinationAccountId` (your own account) or `destinationAccountNumber`
			(anyone's), not both. Send `amount` as a string with at most 2 decimals, e.g. `"250.75"`.
			Debit, credit, transaction record and audit entry are committed together or not at all.

			Send a unique `Idempotency-Key` (e.g. a UUID) to make retries safe: repeating the same request
			with the same key returns the original result (header `Idempotent-Replayed: true`) instead of
			moving money again.

			Transfers above ₹50,000 also need a one-time code: call `POST /transfers/otp` with the same body,
			then send `X-OTP-Challenge` and `X-OTP-Code` with this request.
			""")
	@ApiResponse(responseCode = "201", description = "Transfer completed (or replayed for a repeated Idempotency-Key)")
	@ApiResponse(responseCode = "400", description = "VALIDATION_FAILED, SAME_ACCOUNT, INSUFFICIENT_FUNDS, "
			+ "CURRENCY_MISMATCH, AMOUNT_LIMIT_EXCEEDED, INVALID_IDEMPOTENCY_KEY or OTP_INVALID", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "428", description = "OTP_REQUIRED: large transfer without a one-time code", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "403", description = "Only customers can transfer", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND (also for accounts that are not yours)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "ACCOUNT_NOT_ACTIVE, CONCURRENT_UPDATE or IDEMPOTENCY_KEY_REUSED (same key, different data)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping
	public ResponseEntity<TransferResponse> transfer(@CurrentUserId Long currentUserId,
			@Parameter(description = "Unique per transfer attempt; reuse it only when retrying the same request")
			@RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
			@RequestHeader(name = "X-OTP-Challenge", required = false) String otpChallengeId,
			@RequestHeader(name = "X-OTP-Code", required = false) String otpCode,
			@Valid @RequestBody TransferRequest request) {
		TransferService.TransferResult result = transferService.transferWithStepUp(currentUserId, request,
				idempotencyKey, otpChallengeId, otpCode);
		return ResponseEntity.status(HttpStatus.CREATED)
			.header("Idempotent-Replayed", String.valueOf(result.replayed()))
			.body(result.response());
	}

	@Operation(summary = "Get a one-time code for a large transfer",
			description = "Send the exact transfer body you are about to submit. The code is valid for 5 minutes, "
					+ "for that transfer only, with 3 attempts. DEMO: the code is returned here instead of by SMS.")
	@PostMapping("/otp")
	public OtpService.IssuedOtp requestOtp(@CurrentUserId Long currentUserId, @Valid @RequestBody TransferRequest request) {
		return transferService.requestOtp(currentUserId, request);
	}

}
