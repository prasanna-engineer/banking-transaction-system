package com.example.banking.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.example.banking.entity.User;
import com.example.banking.enums.Role;
import com.example.banking.security.JwtTokenService;
import com.example.banking.support.TestFixtures;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PayeeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JwtTokenService tokenService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private User asha;

	private User ravi;

	private User admin;

	@BeforeEach
	void setUp() {
		TestFixtures fixtures = new TestFixtures(entityManager);
		asha = fixtures.user("Asha Payee", Role.CUSTOMER);
		ravi = fixtures.user("Ravi Payee", Role.CUSTOMER);
		admin = fixtures.user("Admin Payee", Role.ADMIN);
		fixtures.fundedAccount(asha, "300000000001", "100.00");
		fixtures.fundedAccount(ravi, "300000000002", "0.00");
		fixtures.flushAndClear();
	}

	@Test
	void addListAndRemoveAPayee() throws Exception {
		String created = addPayee(asha, "Ravi (rent)", "300000000002").andExpect(status().isCreated())
			.andExpect(jsonPath("$.nickname").value("Ravi (rent)"))
			.andExpect(jsonPath("$.maskedAccountNumber").value("••••0002"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Integer id = JsonPath.read(created, "$.id");

		mockMvc.perform(get("/api/v1/payees").header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].accountNumber").value("300000000002"));

		mockMvc.perform(delete("/api/v1/payees/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/payees").header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(jsonPath("$.length()").value(0));
		org.assertj.core.api.Assertions
			.assertThat(jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'PAYEE' AND entity_id = ?", Integer.class, id))
			.isEqualTo(2);
	}

	@Test
	void rejectsUnknownOwnAndDuplicateAccounts() throws Exception {
		addPayee(asha, "Nobody", "399999999999").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
		addPayee(asha, "Me", "300000000001").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("OWN_ACCOUNT"));
		addPayee(asha, "Ravi", "300000000002").andExpect(status().isCreated());
		addPayee(asha, "Ravi again", "300000000002").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PAYEE_EXISTS"));
		addPayee(asha, "", "abc").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void payeesArePrivateAndCustomerOnly() throws Exception {
		String created = addPayee(asha, "Ravi", "300000000002").andReturn().getResponse().getContentAsString();
		Integer id = JsonPath.read(created, "$.id");

		mockMvc.perform(get("/api/v1/payees").header(HttpHeaders.AUTHORIZATION, bearer(ravi)))
			.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(delete("/api/v1/payees/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(ravi)))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/payees").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(status().isForbidden());
	}

	private ResultActions addPayee(User user, String nickname, String accountNumber) throws Exception {
		return mockMvc.perform(post("/api/v1/payees").header(HttpHeaders.AUTHORIZATION, bearer(user))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\":\"%s\",\"accountNumber\":\"%s\"}".formatted(nickname, accountNumber)));
	}

	private String bearer(User user) {
		return "Bearer " + tokenService.createAccessToken(user.getId(), user.getRole()).value();
	}

}
