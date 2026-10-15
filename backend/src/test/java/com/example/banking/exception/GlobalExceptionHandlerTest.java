package com.example.banking.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plain unit test (no Spring context, no database): a fake controller throws each kind of failure
 * and we check the client gets the right status and code - and never internal details.
 */
class GlobalExceptionHandlerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void lockTimeoutOrDeadlockIsARetryableConflict() throws Exception {
		mockMvc.perform(get("/fail/lock"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"))
			.andExpect(content().string(not(containsString("Deadlock"))));
	}

	@Test
	void staleVersionIsARetryableConflict() throws Exception {
		mockMvc.perform(get("/fail/optimistic"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
	}

	@Test
	void constraintViolationIsAConflictWithoutSqlDetails() throws Exception {
		mockMvc.perform(get("/fail/integrity"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
			.andExpect(content().string(not(containsString("bank_accounts"))));
	}

	@Test
	void unexpectedErrorsRevealNothingInternal() throws Exception {
		mockMvc.perform(get("/fail/unexpected"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.message").value("An unexpected error occurred."))
			.andExpect(content().string(not(containsString("secret-detail"))));
	}

	@RestController
	static class FailingController {

		@GetMapping("/fail/lock")
		void lock() {
			throw new CannotAcquireLockException("Deadlock found when trying to get lock");
		}

		@GetMapping("/fail/optimistic")
		void optimistic() {
			throw new ObjectOptimisticLockingFailureException("BankAccount", 1L);
		}

		@GetMapping("/fail/integrity")
		void integrity() {
			throw new DataIntegrityViolationException("CONSTRAINT failed for bank_accounts",
					new SQLException("CHECK constraint"));
		}

		@GetMapping("/fail/unexpected")
		void unexpected() {
			throw new IllegalStateException("secret-detail: connection string jdbc:mariadb://...");
		}

	}

}
