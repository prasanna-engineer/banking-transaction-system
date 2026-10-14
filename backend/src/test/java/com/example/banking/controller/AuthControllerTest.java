package com.example.banking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.entity.User;
import com.example.banking.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

	private static final String PASSWORD = "Str0ng-Passw0rd";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Test
	void registerCreatesCustomerAndStoresOnlyAPasswordHash() throws Exception {
		register("New Customer", "New.Customer@Example.COM", PASSWORD)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("new.customer@example.com"))
			.andExpect(jsonPath("$.role").value("CUSTOMER"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andExpect(jsonPath("$.password").doesNotExist());

		User saved = userRepository.findByEmail("new.customer@example.com").orElseThrow();
		assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
		assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
	}

	@Test
	void registerCannotGrantAdminRole() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Sneaky","email":"sneaky@example.com","password":"%s","role":"ADMIN"}
					""".formatted(PASSWORD)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.role").value("CUSTOMER"));
	}

	@Test
	void duplicateEmailIsAConflictRegardlessOfCase() throws Exception {
		register("First", "dup@example.com", PASSWORD).andExpect(status().isCreated());

		register("Second", "DUP@example.com", PASSWORD).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
	}

	@Test
	void invalidRegistrationListsFieldErrorsWithoutEchoingValues() throws Exception {
		register("", "not-an-email", "short").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("fullName", "email", "password")))
			.andExpect(content().string(not(containsString("short"))));
	}

	@Test
	void loginReturnsATokenThatOpensProtectedEndpoints() throws Exception {
		register("Login User", "login.user@example.com", PASSWORD).andExpect(status().isCreated());

		String body = login("LOGIN.USER@example.com", PASSWORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(greaterThan(0)))
			.andExpect(jsonPath("$.user.email").value("login.user@example.com"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String token = JsonPath.read(body, "$.accessToken");

		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("login.user@example.com"))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
		register("Real User", "real@example.com", PASSWORD).andExpect(status().isCreated());

		login("real@example.com", "Wrong-Passw0rd").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
			.andExpect(jsonPath("$.message").value("Invalid email or password."));
		login("nobody@example.com", PASSWORD).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
			.andExpect(jsonPath("$.message").value("Invalid email or password."));
	}

	@Test
	void disabledUserCannotLogInAndTheirExistingTokenStopsWorking() throws Exception {
		register("Soon Disabled", "disabled@example.com", PASSWORD).andExpect(status().isCreated());
		String body = login("disabled@example.com", PASSWORD).andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(body, "$.accessToken");

		jdbcTemplate.update("UPDATE users SET status = 'DISABLED' WHERE email = 'disabled@example.com'");
		// The test shares one Hibernate cache across "requests"; real requests each start with an empty one.
		entityManager.clear();

		login("disabled@example.com", PASSWORD).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void malformedJsonIsABadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
	}

	private ResultActions register(String fullName, String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"%s","email":"%s","password":"%s"}
					""".formatted(fullName, email, password)));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email":"%s","password":"%s"}
					""".formatted(email, password)));
	}

}
