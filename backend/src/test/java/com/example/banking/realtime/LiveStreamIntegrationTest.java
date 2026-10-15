package com.example.banking.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.banking.entity.BankAccount;
import com.example.banking.entity.User;
import com.example.banking.enums.Role;
import com.example.banking.security.JwtTokenService;
import com.example.banking.support.TestFixtures;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

/**
 * End to end over HTTP, with real commits (so NOT @Transactional): open the receiver's live stream,
 * make a transfer, and read what was actually written to the stream.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LiveStreamIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService tokenService;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final List<Long> createdUserIds = new ArrayList<>();

	@Test
	void theReceiverSeesTheTransferOnTheirOpenStream() throws Exception {
		User sender = newUser();
		User receiver = newUser();
		BankAccount source = newAccount(sender, "100.00");
		BankAccount destination = newAccount(receiver, "0.00");

		MvcResult stream = openStream(receiver);
		assertThat(stream.getResponse().getContentAsString()).contains("event:connected");

		mockMvc.perform(post("/api/v1/transfers").header(HttpHeaders.AUTHORIZATION, bearer(sender))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"sourceAccountId":%d,"destinationAccountId":%d,"amount":"12.34"}
					""".formatted(source.getId(), destination.getId())))
			.andExpect(status().isCreated());

		// Browsers always decode SSE as UTF-8; MockMvc must be told, or "₹" arrives garbled.
		Awaitility.await().untilAsserted(() -> assertThat(stream.getResponse().getContentAsString(StandardCharsets.UTF_8))
			.contains("event:notification")
			.contains("\"type\":\"MONEY_RECEIVED\"")
			.contains("₹12.34 received"));
	}

	private MvcResult openStream(User user) throws Exception {
		String body = mockMvc
			.perform(post("/api/v1/notifications/stream-ticket").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String ticket = JsonPath.read(body, "$.ticket");
		return mockMvc.perform(get("/api/v1/notifications/stream").param("ticket", ticket))
			.andExpect(request().asyncStarted())
			.andReturn();
	}

	@AfterEach
	void cleanUp() {
		for (Long userId : createdUserIds) {
			jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM idempotency_keys WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM audit_logs WHERE actor_user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM transactions WHERE created_by = ?", userId);
			jdbcTemplate.update("DELETE FROM bank_accounts WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
		}
	}

	private User newUser() {
		User user = transactionTemplate.execute(status -> new TestFixtures(entityManager)
			.user("Live " + UUID.randomUUID().toString().substring(0, 8), Role.CUSTOMER));
		createdUserIds.add(user.getId());
		return user;
	}

	private BankAccount newAccount(User owner, String balance) {
		String number = "4" + String.format("%011d", ThreadLocalRandom.current().nextLong(100_000_000_000L));
		return transactionTemplate.execute(status -> new TestFixtures(entityManager)
			.fundedAccount(entityManager.merge(owner), number, balance));
	}

	private String bearer(User user) {
		return "Bearer " + tokenService.createAccessToken(user.getId(), user.getRole()).value();
	}

}
