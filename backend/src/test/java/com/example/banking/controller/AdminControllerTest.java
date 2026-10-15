package com.example.banking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.entity.AuditLog;
import com.example.banking.entity.User;
import com.example.banking.enums.Role;
import com.example.banking.repository.AuditLogRepository;
import com.example.banking.security.JwtTokenService;
import com.example.banking.support.TestFixtures;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JwtTokenService tokenService;

	@Autowired
	private AuditLogRepository auditLogRepository;

	private TestFixtures fixtures;

	private User admin;

	private User customer;

	@BeforeEach
	void setUp() {
		fixtures = new TestFixtures(entityManager);
		admin = fixtures.user("Admin Panel", Role.ADMIN);
		customer = fixtures.user("Customer Panel", Role.CUSTOMER);
		fixtures.flushAndClear();
	}

	@Test
	void adminListsAndSearchesUsersWithoutPasswordHashes() throws Exception {
		mockMvc.perform(get("/api/v1/admin/users").param("q", "customer.panel").header(HttpHeaders.AUTHORIZATION,
				bearer(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].email").value("customer.panel@example.com"))
			.andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());

		mockMvc.perform(get("/api/v1/admin/users").param("size", "1").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(jsonPath("$.size").value(1))
			.andExpect(jsonPath("$.content[0].id").isNumber());
	}

	@Test
	void customersCannotUseAdminEndpoints() throws Exception {
		mockMvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/admin/audit-logs").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
			.andExpect(status().isForbidden());
		changeStatus(customer, admin.getId(), "DISABLED").andExpect(status().isForbidden());
	}

	@Test
	void disablingAUserIsAuditedAndLocksThemOutImmediately() throws Exception {
		changeStatus(admin, customer.getId(), "DISABLED").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DISABLED"));

		AuditLog audit = auditLogRepository.findByEntityTypeAndEntityId("USER", customer.getId()).getFirst();
		assertThat(audit.getAction()).isEqualTo("USER_STATUS_CHANGED");
		assertThat(audit.getMetadataJson()).contains("\"from\":\"ACTIVE\"").contains("\"to\":\"DISABLED\"");

		fixtures.flushAndClear();
		mockMvc.perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
			.andExpect(status().isUnauthorized());

		changeStatus(admin, customer.getId(), "ACTIVE").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void auditLogsAreListedNewestFirstAndFilterable() throws Exception {
		changeStatus(admin, customer.getId(), "DISABLED").andExpect(status().isOk());
		fixtures.flushAndClear();

		mockMvc.perform(get("/api/v1/admin/audit-logs").param("action", "USER_STATUS_CHANGED")
			.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].action").value("USER_STATUS_CHANGED"))
			.andExpect(jsonPath("$.content[0].actorEmail").value("admin.panel@example.com"))
			.andExpect(jsonPath("$.content[*].entityId", hasItem(customer.getId().intValue())));
	}

	@Test
	void adminCannotChangeTheirOwnStatus() throws Exception {
		changeStatus(admin, admin.getId(), "DISABLED").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CANNOT_CHANGE_OWN_STATUS"));
	}

	@Test
	void unknownUserAndInvalidStatusAreRejected() throws Exception {
		changeStatus(admin, Long.MAX_VALUE, "DISABLED").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
		changeStatus(admin, customer.getId(), "BANNED").andExpect(status().isBadRequest());
	}

	private ResultActions changeStatus(User caller, Long userId, String status) throws Exception {
		return mockMvc.perform(patch("/api/v1/admin/users/{id}/status", userId)
			.header(HttpHeaders.AUTHORIZATION, bearer(caller))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"" + status + "\"}"));
	}

	private String bearer(User user) {
		return "Bearer " + tokenService.createAccessToken(user.getId(), user.getRole()).value();
	}

}
