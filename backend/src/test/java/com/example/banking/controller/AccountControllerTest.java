package com.example.banking.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.entity.BankAccount;
import com.example.banking.entity.User;
import com.example.banking.enums.Role;
import com.example.banking.security.DemoUserHeader;
import com.example.banking.support.TestFixtures;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional // every test's rows are rolled back afterwards
class AccountControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	private User asha;

	private User admin;

	private BankAccount ashaSavings;

	private BankAccount raviSavings;

	@BeforeEach
	void setUp() {
		TestFixtures fixtures = new TestFixtures(entityManager);
		asha = fixtures.user("Asha Test", Role.CUSTOMER);
		User ravi = fixtures.user("Ravi Test", Role.CUSTOMER);
		admin = fixtures.user("Admin Test", Role.ADMIN);
		ashaSavings = fixtures.account(asha, "800000000001");
		fixtures.account(asha, "800000000002");
		raviSavings = fixtures.account(ravi, "800000000003");
		fixtures.flushAndClear();
	}

	@Test
	void listReturnsOnlyTheCallersAccounts() throws Exception {
		mockMvc.perform(get("/api/v1/accounts").header(DemoUserHeader.NAME, asha.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[*].accountNumber", contains("800000000001", "800000000002")));
	}

	@Test
	void ownAccountIsReturnedWithBalanceAsString() throws Exception {
		mockMvc.perform(get("/api/v1/accounts/{id}", ashaSavings.getId()).header(DemoUserHeader.NAME, asha.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accountNumber").value("800000000001"))
			.andExpect(jsonPath("$.balance").value("0.00"))
			.andExpect(jsonPath("$.currency").value("INR"))
			.andExpect(jsonPath("$.version").doesNotExist());
	}

	@Test
	void anotherCustomersAccountLooksExactlyLikeAMissingOne() throws Exception {
		mockMvc.perform(get("/api/v1/accounts/{id}", raviSavings.getId()).header(DemoUserHeader.NAME, asha.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
			.andExpect(jsonPath("$.message").value("Account not found."))
			.andExpect(jsonPath("$.accountNumber").doesNotExist());

		mockMvc.perform(get("/api/v1/accounts/{id}", Long.MAX_VALUE).header(DemoUserHeader.NAME, asha.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
			.andExpect(jsonPath("$.message").value("Account not found."));
	}

	@Test
	void adminCanReadAnyAccount() throws Exception {
		mockMvc.perform(get("/api/v1/accounts/{id}", raviSavings.getId()).header(DemoUserHeader.NAME, admin.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accountNumber").value("800000000003"));
	}

	@Test
	void missingCallerIsUnauthenticated() throws Exception {
		mockMvc.perform(get("/api/v1/accounts"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
			.andExpect(jsonPath("$.path").value("/api/v1/accounts"));
	}

	@Test
	void unknownCallerIsUnauthenticated() throws Exception {
		mockMvc.perform(get("/api/v1/accounts").header(DemoUserHeader.NAME, Long.MAX_VALUE))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void nonNumericIdIsABadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/accounts/abc").header(DemoUserHeader.NAME, asha.getId()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
	}

}
