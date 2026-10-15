package com.example.banking.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.YearMonth;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
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
class InsightsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JwtTokenService tokenService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private User asha;

	@BeforeEach
	void setUp() {
		TestFixtures fixtures = new TestFixtures(entityManager);
		asha = fixtures.user("Asha Insight", Role.CUSTOMER);
		User ravi = fixtures.user("Ravi Insight", Role.CUSTOMER);
		BankAccount ashaSavings = fixtures.account(asha, "200000000001");
		BankAccount ashaCurrent = fixtures.account(asha, "200000000002");
		BankAccount raviSavings = fixtures.account(ravi, "200000000003");

		fixtures.deposit(ashaSavings, "1000.00", asha); // in
		fixtures.transfer(raviSavings, ashaSavings, "250.50", ravi); // in
		fixtures.transfer(ashaSavings, raviSavings, "100.25", asha); // out
		fixtures.transfer(ashaSavings, ashaCurrent, "400.00", asha); // internal: neither
		BankTransaction old = fixtures.transfer(ashaSavings, raviSavings, "70.00", asha); // out, last month
		fixtures.flushAndClear();
		jdbcTemplate.update("UPDATE transactions SET created_at = ? WHERE id = ?",
				YearMonth.now(ZoneOffset.UTC).minusMonths(1).atDay(15).atStartOfDay(), old.getId());
	}

	@Test
	void sumsMoneyInAndOutPerMonthAndIgnoresMovesBetweenOwnAccounts() throws Exception {
		String thisMonth = YearMonth.now(ZoneOffset.UTC).toString();
		String lastMonth = YearMonth.now(ZoneOffset.UTC).minusMonths(1).toString();

		mockMvc.perform(get("/api/v1/insights/monthly-flow").param("months", "3").header(HttpHeaders.AUTHORIZATION,
				bearer(asha)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currency").value("INR"))
			.andExpect(jsonPath("$.months", hasSize(3)))
			// oldest first, every month present
			.andExpect(jsonPath("$.months[0].moneyIn").value("0.00"))
			.andExpect(jsonPath("$.months[1].month").value(lastMonth))
			.andExpect(jsonPath("$.months[1].moneyOut").value("70.00"))
			.andExpect(jsonPath("$.months[2].month").value(thisMonth))
			.andExpect(jsonPath("$.months[2].moneyIn").value("1250.50"))
			.andExpect(jsonPath("$.months[2].moneyOut").value("100.25"));
	}

	@Test
	void rejectsAnUnreasonableRange() throws Exception {
		mockMvc.perform(get("/api/v1/insights/monthly-flow").param("months", "60").header(HttpHeaders.AUTHORIZATION,
				bearer(asha)))
			.andExpect(status().isBadRequest());
	}

	private String bearer(User user) {
		return "Bearer " + tokenService.createAccessToken(user.getId(), user.getRole()).value();
	}

}
