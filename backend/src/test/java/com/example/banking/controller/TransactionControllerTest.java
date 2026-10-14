package com.example.banking.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;

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
class TransactionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JwtTokenService tokenService;

	private User asha;

	private User admin;

	private BankAccount ashaCurrent;

	private BankAccount raviSavings;

	private BankTransaction ashaDeposit;

	private BankTransaction ashaToRavi;

	private BankTransaction ashaToSelf;

	private BankTransaction raviOnlyDeposit;

	/*
	 * Asha sees: ashaDeposit, ashaToRavi, ashaToSelf          (3)
	 * Ravi sees: raviDeposit, ashaToRavi, raviOnlyDeposit     (3)
	 * Admin sees all five.
	 */
	@BeforeEach
	void setUp() {
		TestFixtures fixtures = new TestFixtures(entityManager);
		asha = fixtures.user("Asha Test", Role.CUSTOMER);
		User ravi = fixtures.user("Ravi Test", Role.CUSTOMER);
		admin = fixtures.user("Admin Test", Role.ADMIN);
		BankAccount ashaSavings = fixtures.account(asha, "800000000001");
		ashaCurrent = fixtures.account(asha, "800000000002");
		raviSavings = fixtures.account(ravi, "800000000003");

		ashaDeposit = fixtures.deposit(ashaSavings, "1000.00", admin);
		fixtures.deposit(raviSavings, "500.00", admin);
		ashaToRavi = fixtures.transfer(ashaSavings, raviSavings, "200.00", asha);
		ashaToSelf = fixtures.transfer(ashaSavings, ashaCurrent, "100.00", asha);
		raviOnlyDeposit = fixtures.deposit(raviSavings, "50.00", admin);
		fixtures.flushAndClear();
	}

	@Test
	void customerSeesOnlyTransactionsTouchingTheirAccounts() throws Exception {
		mockMvc.perform(get("/api/v1/transactions").header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.content[*].reference", containsInAnyOrder(ashaDeposit.getReference(),
					ashaToRavi.getReference(), ashaToSelf.getReference())));
	}

	@Test
	void adminSeesAllTransactions() throws Exception {
		mockMvc.perform(get("/api/v1/transactions").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(5));
	}

	@Test
	void filtersByTypeAndAccount() throws Exception {
		mockMvc.perform(get("/api/v1/transactions").param("type", "TRANSFER").header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(jsonPath("$.totalElements").value(2));

		mockMvc
			.perform(get("/api/v1/transactions").param("accountId", ashaCurrent.getId().toString())
				.header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].reference").value(ashaToSelf.getReference()));
	}

	@Test
	void filteringByAnotherCustomersAccountIsNotFound() throws Exception {
		mockMvc
			.perform(get("/api/v1/transactions").param("accountId", raviSavings.getId().toString())
				.header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
	}

	@Test
	void paginatesWithStableNewestFirstOrderAndMetadata() throws Exception {
		mockMvc
			.perform(get("/api/v1/transactions").param("size", "2").param("page", "0")
				.header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(jsonPath("$.content", hasSize(2)))
			.andExpect(jsonPath("$.content[0].reference").value(ashaToSelf.getReference()))
			.andExpect(jsonPath("$.content[1].reference").value(ashaToRavi.getReference()))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(2))
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.first").value(true))
			.andExpect(jsonPath("$.last").value(false));

		mockMvc
			.perform(get("/api/v1/transactions").param("size", "2").param("page", "1")
				.header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(jsonPath("$.content", hasSize(1)))
			.andExpect(jsonPath("$.content[0].reference").value(ashaDeposit.getReference()))
			.andExpect(jsonPath("$.last").value(true));
	}

	@Test
	void pageSizeIsCappedSoHistoryCanNeverBeDumpedInOneRequest() throws Exception {
		mockMvc.perform(get("/api/v1/transactions").param("size", "100000").header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.size").value(100));
	}

	@Test
	void filtersByDateRange() throws Exception {
		String today = LocalDate.now(ZoneOffset.UTC).toString();
		String yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString();

		mockMvc
			.perform(get("/api/v1/transactions").param("from", today).param("to", today)
				.header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(jsonPath("$.totalElements").value(3));

		mockMvc
			.perform(get("/api/v1/transactions").param("to", yesterday).header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void rejectsInvalidQueryParameters() throws Exception {
		mockMvc
			.perform(get("/api/v1/transactions").param("from", "2026-10-14").param("to", "2026-10-01")
				.header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));

		mockMvc.perform(get("/api/v1/transactions").param("sort", "passwordHash").header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SORT"));

		mockMvc.perform(get("/api/v1/transactions").param("type", "SHOPPING").header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
	}

	@Test
	void transactionDetailMasksAccountNumbersAndSendsAmountAsString() throws Exception {
		mockMvc.perform(get("/api/v1/transactions/{id}", ashaToRavi.getId()).header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value("TRANSFER"))
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.amount").value("200.00"))
			.andExpect(jsonPath("$.maskedDestinationAccountNumber").value("********0003"))
			.andExpect(jsonPath("$.destinationAccountNumber").doesNotExist());
	}

	@Test
	void unrelatedTransactionIsNotFound() throws Exception {
		mockMvc
			.perform(get("/api/v1/transactions/{id}", raviOnlyDeposit.getId()).header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
	}

	private String bearer(User user) {
		return "Bearer " + tokenService.createAccessToken(user.getId(), user.getRole()).value();
	}

}
