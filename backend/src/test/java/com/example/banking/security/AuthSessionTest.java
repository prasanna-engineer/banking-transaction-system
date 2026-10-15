package com.example.banking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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

import com.example.banking.service.MaintenanceJobs;
import com.jayway.jsonpath.JsonPath;

/**
 * Sessions over HTTP with REAL commits (not @Transactional): the important property of reuse detection
 * is that the revocation is actually committed even though the request that triggers it fails.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSessionTest {

	private static final String PASSWORD = "Str0ng-Passw0rd";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private MaintenanceJobs maintenanceJobs;

	private final List<String> createdEmails = new ArrayList<>();

	@Test
	void refreshRotatesTheTokenAndANewAccessTokenWorks() throws Exception {
		String email = newUser();
		String login = login(email, PASSWORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.expiresIn").value(org.hamcrest.Matchers.lessThanOrEqualTo(900)))
			.andExpect(jsonPath("$.refreshExpiresIn").value(org.hamcrest.Matchers.greaterThan(600_000)))
			.andReturn()
			.getResponse()
			.getContentAsString();

		String refreshed = refresh(JsonPath.read(login, "$.refreshToken")).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat((String) JsonPath.read(refreshed, "$.refreshToken")).isNotEqualTo(JsonPath.read(login, "$.refreshToken"));
		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.read(refreshed, "$.accessToken")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void reusingAnOldRefreshTokenRevokesTheWholeSession() throws Exception {
		String email = newUser();
		String first = JsonPath.read(login(email, PASSWORD).andReturn().getResponse().getContentAsString(), "$.refreshToken");
		String second = JsonPath.read(refresh(first).andReturn().getResponse().getContentAsString(), "$.refreshToken");

		// An attacker replays the stolen, already-used first token.
		refresh(first).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
		// ...which must also have killed the legitimate user's current token (committed, not rolled back).
		refresh(second).andExpect(status().isUnauthorized());
	}

	@Test
	void logoutEndsTheSession() throws Exception {
		String email = newUser();
		String token = JsonPath.read(login(email, PASSWORD).andReturn().getResponse().getContentAsString(), "$.refreshToken");

		mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + token + "\"}"))
			.andExpect(status().isNoContent());
		refresh(token).andExpect(status().isUnauthorized());
	}

	@Test
	void signingOutEverywhereKillsAccessAndRefreshTokensAtOnce() throws Exception {
		String email = newUser();
		String sessionA = login(email, PASSWORD).andReturn().getResponse().getContentAsString();
		String sessionB = login(email, PASSWORD).andReturn().getResponse().getContentAsString();
		String accessA = JsonPath.read(sessionA, "$.accessToken");

		mockMvc.perform(post("/api/v1/auth/logout-all").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA))
			.andExpect(status().isNoContent());

		// The access token has not expired, but it was issued before the sign-out: rejected.
		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
		refresh(JsonPath.read(sessionB, "$.refreshToken")).andExpect(status().isUnauthorized());
		// A fresh sign-in works again.
		login(email, PASSWORD).andExpect(status().isOk());
	}

	@Test
	void fiveFailedSignInsLockTheEmailEvenForTheRightPassword() throws Exception {
		String email = newUser();
		for (int i = 0; i < 5; i++) {
			login(email, "Wrong-Passw0rd").andExpect(status().isUnauthorized());
		}
		login(email, PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"))
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
	}

	@Test
	void unknownEmailsAreLockedTheSameWaySoLockingRevealsNothing() throws Exception {
		String nobody = "nobody-" + UUID.randomUUID() + "@example.com";
		for (int i = 0; i < 5; i++) {
			login(nobody, "whatever-123").andExpect(status().isUnauthorized());
		}
		login(nobody, "whatever-123").andExpect(status().isTooManyRequests());
	}

	@Test
	void housekeepingRemovesIdempotencyKeysOlderThan24Hours() throws Exception {
		String email = newUser();
		Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
		jdbcTemplate.update("INSERT INTO idempotency_keys (user_id, idempotency_key, request_hash, created_at) VALUES (?, ?, ?, ?)",
				userId, "old-key-123", "x".repeat(64), utc(Instant.now().minusSeconds(25 * 3600)));
		jdbcTemplate.update("INSERT INTO idempotency_keys (user_id, idempotency_key, request_hash, created_at) VALUES (?, ?, ?, ?)",
				userId, "new-key-123", "x".repeat(64), utc(Instant.now()));

		maintenanceJobs.cleanUp();

		assertThat(jdbcTemplate.queryForList("SELECT idempotency_key FROM idempotency_keys WHERE user_id = ?", String.class, userId))
			.containsExactly("new-key-123");
	}

	@AfterEach
	void cleanUp() {
		for (String email : createdEmails) {
			Long id = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
			jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", id);
			jdbcTemplate.update("DELETE FROM idempotency_keys WHERE user_id = ?", id);
			jdbcTemplate.update("DELETE FROM audit_logs WHERE actor_user_id = ?", id);
			jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
		}
	}

	// The app stores UTC (hibernate.jdbc.time_zone=UTC). Plain JDBC would convert using this PC's zone
	// (e.g. IST, +5:30), making "25 hours ago" look like 19.5 hours ago to the app.
	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private String newUser() {
		String email = "session-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		jdbcTemplate.update("INSERT INTO users (full_name, email, password_hash, role, status, created_at, updated_at) "
				+ "VALUES ('Session Test', ?, ?, 'CUSTOMER', 'ACTIVE', NOW(6), NOW(6))", email, passwordEncoder.encode(PASSWORD));
		createdEmails.add(email);
		return email;
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + refreshToken + "\"}"));
	}

}
