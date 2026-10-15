package com.example.banking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.entity.AuditLog;
import com.example.banking.entity.BankAccount;
import com.example.banking.entity.User;
import com.example.banking.enums.Role;
import com.example.banking.repository.AuditLogRepository;
import com.example.banking.repository.BankAccountRepository;
import com.example.banking.security.JwtTokenService;
import com.example.banking.support.TestFixtures;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

/** Business rules of a single transfer. Concurrency and rollback are in TransferConcurrencyTest. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransferControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JwtTokenService tokenService;

	@Autowired
	private BankAccountRepository accountRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private TestFixtures fixtures;

	private User asha;

	private User ravi;

	private User admin;

	private BankAccount ashaSavings;

	private BankAccount ashaCurrent;

	private BankAccount raviSavings;

	@BeforeEach
	void setUp() {
		fixtures = new TestFixtures(entityManager);
		asha = fixtures.user("Asha Transfer", Role.CUSTOMER);
		ravi = fixtures.user("Ravi Transfer", Role.CUSTOMER);
		admin = fixtures.user("Admin Transfer", Role.ADMIN);
		ashaSavings = fixtures.fundedAccount(asha, "700000000001", "1000.00");
		ashaCurrent = fixtures.fundedAccount(asha, "700000000002", "50.00");
		raviSavings = fixtures.fundedAccount(ravi, "700000000003", "300.00");
		fixtures.flushAndClear();
	}

	@Test
	void successfulTransferMovesExactlyTheAmountAndRecordsTransactionAndAudit() throws Exception {
		String body = transfer(asha, ashaSavings, raviSavings, "\"250.75\"")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.reference").value(matchesPattern("TRX-\\d{8}-[A-Z0-9]{10}")))
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.amount").value("250.75"))
			.andExpect(jsonPath("$.currency").value("INR"))
			.andExpect(jsonPath("$.sourceAccountId").value(ashaSavings.getId()))
			.andExpect(jsonPath("$.destinationAccountId").value(raviSavings.getId()))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertBalance(ashaSavings, "749.25");
		assertBalance(raviSavings, "550.75");
		String reference = JsonPath.read(body, "$.reference");
		assertThat(countTransactions()).isEqualTo(1);
		Long txId = jdbcTemplate.queryForObject("SELECT id FROM transactions WHERE reference = ?", Long.class,
				reference);
		AuditLog audit = auditLogRepository.findByEntityTypeAndEntityId("TRANSACTION", txId).getFirst();
		assertThat(audit.getAction()).isEqualTo("TRANSFER_COMPLETED");
		assertThat(audit.getMetadataJson()).contains(reference).contains("\"amount\":\"250.75\"");
	}

	@Test
	void amountMayBeSentAsAJsonNumberToo() throws Exception {
		transfer(asha, ashaSavings, ashaCurrent, "100").andExpect(status().isCreated())
			.andExpect(jsonPath("$.amount").value("100.00"));

		assertBalance(ashaSavings, "900.00");
		assertBalance(ashaCurrent, "150.00");
	}

	@Test
	void zeroNegativeAndOverPreciseAmountsAreRejected() throws Exception {
		for (String amount : new String[] { "\"0\"", "\"-10.00\"", "\"10.005\"" }) {
			transfer(asha, ashaSavings, raviSavings, amount).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
		}
		assertUnchanged();
	}

	@Test
	void amountAboveTheDemoLimitIsRejected() throws Exception {
		transfer(asha, ashaSavings, raviSavings, "\"1000000.01\"").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AMOUNT_LIMIT_EXCEEDED"));
		assertUnchanged();
	}

	@Test
	void insufficientFundsIsRejectedAndNothingChanges() throws Exception {
		transfer(asha, ashaCurrent, raviSavings, "\"50.01\"").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
			.andExpect(jsonPath("$.message").value("The source account has insufficient funds."));
		assertUnchanged();
	}

	@Test
	void exactBalanceCanBeTransferredLeavingZero() throws Exception {
		transfer(asha, ashaCurrent, raviSavings, "\"50.00\"").andExpect(status().isCreated());
		assertBalance(ashaCurrent, "0.00");
	}

	@Test
	void transferToTheSameAccountIsRejected() throws Exception {
		transfer(asha, ashaSavings, ashaSavings, "\"10.00\"").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SAME_ACCOUNT"));
		assertUnchanged();
	}

	@Test
	void cannotSpendFromSomeoneElsesAccount() throws Exception {
		transfer(asha, raviSavings, ashaSavings, "\"10.00\"").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
		assertUnchanged();
	}

	@Test
	void unknownDestinationIsNotFound() throws Exception {
		mockMvc.perform(post("/api/v1/transfers").header(HttpHeaders.AUTHORIZATION, bearer(asha))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"sourceAccountId":%d,"destinationAccountId":%d,"amount":"10.00"}
					""".formatted(ashaSavings.getId(), Long.MAX_VALUE)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
		assertUnchanged();
	}

	@Test
	void inactiveDestinationIsAConflict() throws Exception {
		jdbcTemplate.update("UPDATE bank_accounts SET status = 'FROZEN' WHERE id = ?", raviSavings.getId());
		entityManager.clear();

		transfer(asha, ashaSavings, raviSavings, "\"10.00\"").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
		assertBalance(ashaSavings, "1000.00");
	}

	@Test
	void differentCurrenciesAreRejected() throws Exception {
		BankAccount usd = fixtures.fundedAccount(ravi, "700000000004", "10.00", "USD");
		fixtures.flushAndClear();

		transfer(asha, ashaSavings, usd, "\"10.00\"").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
		assertBalance(ashaSavings, "1000.00");
	}

	@Test
	void missingFieldsAreValidationErrors() throws Exception {
		mockMvc.perform(post("/api/v1/transfers").header(HttpHeaders.AUTHORIZATION, bearer(asha))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void adminsCannotMoveMoney() throws Exception {
		transfer(admin, ashaSavings, raviSavings, "\"10.00\"").andExpect(status().isForbidden());
		assertUnchanged();
	}

	@Test
	void anonymousRequestsAreRejected() throws Exception {
		mockMvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions transfer(User caller, BankAccount source, BankAccount destination, String amountJson)
			throws Exception {
		return mockMvc.perform(post("/api/v1/transfers").header(HttpHeaders.AUTHORIZATION, bearer(caller))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"sourceAccountId":%d,"destinationAccountId":%d,"amount":%s,"description":"Test"}
					""".formatted(source.getId(), destination.getId(), amountJson)));
	}

	private void assertUnchanged() {
		assertBalance(ashaSavings, "1000.00");
		assertBalance(ashaCurrent, "50.00");
		assertBalance(raviSavings, "300.00");
		assertThat(countTransactions()).isZero();
	}

	private void assertBalance(BankAccount account, String expected) {
		// The test's transaction never commits, so write pending balance changes before emptying the cache.
		fixtures.flushAndClear();
		BigDecimal actual = accountRepository.findById(account.getId()).orElseThrow().getBalance();
		assertThat(actual).isEqualByComparingTo(expected);
	}

	private int countTransactions() {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM transactions WHERE source_account_id IN (?, ?, ?) OR destination_account_id IN (?, ?, ?)",
				Integer.class, ashaSavings.getId(), ashaCurrent.getId(), raviSavings.getId(), ashaSavings.getId(),
				ashaCurrent.getId(), raviSavings.getId());
	}

	private String bearer(User user) {
		return "Bearer " + tokenService.createAccessToken(user.getId(), user.getRole()).value();
	}

}
