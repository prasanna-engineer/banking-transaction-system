package com.example.banking.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.banking.dto.PageResponse;
import com.example.banking.dto.transaction.TransactionFilter;
import com.example.banking.dto.transaction.TransactionResponse;
import com.example.banking.enums.TransactionStatus;
import com.example.banking.enums.TransactionType;
import com.example.banking.security.CurrentUserId;
import com.example.banking.service.TransactionService;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Transactions", description = "Paginated, filterable transaction history")
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

	private final TransactionService transactionService;

	public TransactionController(TransactionService transactionService) {
		this.transactionService = transactionService;
	}

	// GET /api/v1/transactions?accountId=1&from=2026-10-01&to=2026-10-14&type=TRANSFER&page=0&size=20&sort=createdAt,desc
	@GetMapping
	public PageResponse<TransactionResponse> search(@CurrentUserId Long currentUserId,
			@RequestParam(required = false) Long accountId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) TransactionType type,
			@RequestParam(required = false) TransactionStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "createdAt,desc") String sort) {
		return transactionService.search(currentUserId, new TransactionFilter(accountId, from, to, type, status), page,
				size, sort);
	}

	/** Same filters as the list, as a downloadable CSV statement. */
	@GetMapping(path = "/export", produces = "text/csv")
	public ResponseEntity<String> export(@CurrentUserId Long currentUserId,
			@RequestParam(required = false) Long accountId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) TransactionType type,
			@RequestParam(required = false) TransactionStatus status) {
		String csv = transactionService.exportCsv(currentUserId, new TransactionFilter(accountId, from, to, type, status));
		String filename = "transactions-" + LocalDate.now(ZoneOffset.UTC) + ".csv";
		return ResponseEntity.ok()
			.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
			.body(csv);
	}

	@GetMapping("/{id}")
	public TransactionResponse get(@CurrentUserId Long currentUserId, @PathVariable Long id) {
		return transactionService.getTransaction(currentUserId, id);
	}

}
