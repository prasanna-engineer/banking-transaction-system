package com.example.banking.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.entity.BankAccount;
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
class NotificationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JwtTokenService tokenService;

	private User asha;

	private User ravi;

	private BankAccount ashaSavings;

	private BankAccount ashaCurrent;

	private BankAccount raviSavings;

	@BeforeEach
	void setUp() {
		TestFixtures fixtures = new TestFixtures(entityManager);
		asha = fixtures.user("Asha Notify", Role.CUSTOMER);
		ravi = fixtures.user("Ravi Notify", Role.CUSTOMER);
		ashaSavings = fixtures.fundedAccount(asha, "500000000001", "1000.00");
		ashaCurrent = fixtures.fundedAccount(asha, "500000000002", "0.00");
		raviSavings = fixtures.fundedAccount(ravi, "500000000003", "0.00");
		fixtures.flushAndClear();
	}

	@Test
	void aTransferNotifiesTheSenderAndTheReceiver() throws Exception {
		transfer(ashaSavings, raviSavings, "250.00");

		mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, bearer(ravi)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].type").value("MONEY_RECEIVED"))
			.andExpect(jsonPath("$.content[0].message").value("₹250.00 received in ••••0003 from ••••0001."))
			.andExpect(jsonPath("$.content[0].read").value(false));
		mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(jsonPath("$.content[0].type").value("MONEY_SENT"));
	}

	@Test
	void aTransferBetweenOwnAccountsCreatesOneNotification() throws Exception {
		transfer(ashaSavings, ashaCurrent, "10.00");

		mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].type").value("INTERNAL_TRANSFER"));
	}

	@Test
	void unreadCountAndMarkingAsRead() throws Exception {
		transfer(ashaSavings, raviSavings, "1.00");
		transfer(ashaSavings, raviSavings, "2.00");
		unreadCount(ravi, 2);

		String list = mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, bearer(ravi)))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Integer firstId = JsonPath.read(list, "$.content[0].id");

		mockMvc.perform(patch("/api/v1/notifications/{id}/read", firstId).header(HttpHeaders.AUTHORIZATION, bearer(ravi)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.read").value(true));
		unreadCount(ravi, 1);

		mockMvc.perform(post("/api/v1/notifications/read-all").header(HttpHeaders.AUTHORIZATION, bearer(ravi)))
			.andExpect(jsonPath("$.updated").value(1));
		unreadCount(ravi, 0);
	}

	@Test
	void someoneElsesNotificationIsNotFound() throws Exception {
		transfer(ashaSavings, raviSavings, "1.00");
		String list = mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, bearer(ravi)))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Integer ravisNotification = JsonPath.read(list, "$.content[0].id");

		mockMvc
			.perform(patch("/api/v1/notifications/{id}/read", ravisNotification).header(HttpHeaders.AUTHORIZATION,
					bearer(asha)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
	}

	@Test
	void theLiveStreamOpensOnlyWithAValidSingleUseTicket() throws Exception {
		mockMvc.perform(post("/api/v1/notifications/stream-ticket")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/notifications/stream").param("ticket", "made-up")).andExpect(status().isUnauthorized());

		String body = mockMvc
			.perform(post("/api/v1/notifications/stream-ticket").header(HttpHeaders.AUTHORIZATION, bearer(asha)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String ticket = JsonPath.read(body, "$.ticket");

		mockMvc.perform(get("/api/v1/notifications/stream").param("ticket", ticket)).andExpect(request().asyncStarted());
		// The same ticket cannot be used twice.
		mockMvc.perform(get("/api/v1/notifications/stream").param("ticket", ticket)).andExpect(status().isUnauthorized());
	}

	private void transfer(BankAccount source, BankAccount destination, String amount) throws Exception {
		mockMvc.perform(post("/api/v1/transfers").header(HttpHeaders.AUTHORIZATION, bearer(source.getUser()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"sourceAccountId":%d,"destinationAccountId":%d,"amount":"%s"}
					""".formatted(source.getId(), destination.getId(), amount)))
			.andExpect(status().isCreated());
	}

	private void unreadCount(User user, int expected) throws Exception {
		mockMvc.perform(get("/api/v1/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(jsonPath("$.count").value(expected));
	}

	private String bearer(User user) {
		return "Bearer " + tokenService.createAccessToken(user.getId(), user.getRole()).value();
	}

}
