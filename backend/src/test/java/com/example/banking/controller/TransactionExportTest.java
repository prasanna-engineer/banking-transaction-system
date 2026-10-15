package com.example.banking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.entity.BankAccount;
import com.example.banking.entity.BankTransaction;
import com.example.banking.entity.User;
import com.example.banking.enums.Role;
import com.example.banking.security.JwtTokenService;
import com.example.banking.support.TestFixtures;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransactionExportTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JwtTokenService tokenService;

	private User asha;

	private User ravi;

	private BankAccount raviSavings;

	@BeforeEach
	void setUp() {
		TestFixtures fixtures = new TestFixtures(entityManager);
		asha = fixtures.user("Asha Export", Role.CUSTOMER);
		ravi = fixtures.user("Ravi Export", Role.CUSTOMER);
		BankAccount ashaSavings = fixtures.account(asha, "100000009991");
		raviSavings = fixtures.account(ravi, "100000009992");
		fixtures.deposit(ashaSavings, "500.00", asha);
		// A description crafted to run as a spreadsheet formula when the CSV is opened.
		entityManager.persist(BankTransaction.transfer("TRX-EXPORT-EVIL", ashaSavings, raviSavings,
				new BigDecimal("12.50"), "=HYPERLINK(\"http://evil.example\",\"Click\")", asha));
		fixtures.flushAndClear();
	}

	@Test
	void exportsTheCallersTransactionsAsASafeCsvFile() throws Exception {
		var response = mockMvc.perform(get("/api/v1/transactions/export").header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
					org.hamcrest.Matchers.startsWith("attachment; filename=\"transactions-")))
			.andReturn()
			.getResponse();
		String csv = response.getContentAsString(StandardCharsets.UTF_8);
		String[] lines = csv.substring(1).split("\r\n"); // skip the UTF-8 byte-order mark

		assertThat(csv).startsWith("﻿");
		assertThat(lines[0]).isEqualTo("Date (UTC),Reference,Type,Status,Direction,From,To,Amount,Currency,Description");
		assertThat(lines).hasSize(3);
		assertThat(csv).contains("\"TRX-EXPORT-EVIL\",\"TRANSFER\",\"COMPLETED\",\"Debit\",\"****9991\",\"****9992\",\"12.50\",\"INR\"");
		assertThat(csv).contains("\"Credit\",\"Deposit\",\"****9991\",\"500.00\"");
		// The formula is defused (leading quote) and inner quotes are doubled - it opens as plain text.
		assertThat(csv).contains("\"'=HYPERLINK(\"\"http://evil.example\"\",\"\"Click\"\")\"");
	}

	@Test
	void theReceiverSeesTheSameTransferAsACredit() throws Exception {
		String csv = mockMvc.perform(get("/api/v1/transactions/export").header(HttpHeaders.AUTHORIZATION, bearer(ravi)))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		assertThat(csv).contains("\"TRX-EXPORT-EVIL\",\"TRANSFER\",\"COMPLETED\",\"Credit\"").doesNotContain("500.00");
	}

	@Test
	void ownershipRulesMatchTheListEndpoint() throws Exception {
		mockMvc.perform(get("/api/v1/transactions/export").param("accountId", raviSavings.getId().toString())
			.header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/transactions/export")).andExpect(status().isUnauthorized());
	}

	private String bearer(User user) {
		return "Bearer " + tokenService.createAccessToken(user.getId(), user.getRole()).value();
	}

}
