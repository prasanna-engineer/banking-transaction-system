package com.example.banking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.banking.dto.transfer.TransferRequest;
import com.example.banking.entity.BankAccount;
import com.example.banking.entity.User;
import com.example.banking.enums.Role;
import com.example.banking.exception.ApiException;
import com.example.banking.realtime.LiveEvents;
import com.example.banking.realtime.SseHub;
import com.example.banking.support.TestFixtures;

import jakarta.persistence.EntityManager;

/**
 * Deliberately NOT @Transactional: these tests need real commits, real rollbacks and several threads,
 * each with its own database connection - exactly like simultaneous HTTP requests in production.
 * Rows are created with unique names and deleted again in cleanUp().
 */
@SpringBootTest
@ActiveProfiles("test")
class TransferConcurrencyTest {

	@Autowired
	private TransferService transferService;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// A real AuditService that a test can tell to fail on demand.
	@MockitoSpyBean
	private AuditService auditService;

	// The real hub, watched so tests can check what was pushed to browsers and when.
	@MockitoSpyBean
	private SseHub sseHub;

	private final List<Long> createdUserIds = new ArrayList<>();

	@Test
	void concurrentTransfersCanNeverOverdrawAnAccount() throws Exception {
		User owner = newUser();
		BankAccount source = newAccount(owner, "100.00");
		BankAccount destination = newAccount(owner, "0.00");

		// 20 simultaneous transfers of 10.00 from an account holding 100.00: exactly 10 may succeed.
		List<Outcome> outcomes = runConcurrently(20, i -> () -> transferService.transfer(owner.getId(),
				new TransferRequest(source.getId(), destination.getId(), new BigDecimal("10.00"), "race " + i)));

		assertThat(outcomes.stream().filter(outcome -> outcome.succeeded()).count()).isEqualTo(10);
		assertThat(outcomes.stream().filter(o -> "INSUFFICIENT_FUNDS".equals(o.errorCode())).count()).isEqualTo(10);
		assertThat(balanceOf(source)).isEqualByComparingTo("0.00");
		assertThat(balanceOf(destination)).isEqualByComparingTo("100.00");
		assertThat(transactionCount(source)).isEqualTo(10);
	}

	@Test
	void opposingTransfersDoNotDeadlockOrLoseMoney() throws Exception {
		User owner = newUser();
		BankAccount a = newAccount(owner, "100.00");
		BankAccount b = newAccount(owner, "100.00");

		// 40 A->B and 40 B->A at the same time. Without ordered locking, MariaDB would detect deadlocks
		// (each transfer holding one row and waiting for the other) and abort some of them.
		List<Outcome> outcomes = runConcurrently(80, i -> () -> {
			boolean aToB = i % 2 == 0;
			return transferService.transfer(owner.getId(), new TransferRequest(aToB ? a.getId() : b.getId(),
					aToB ? b.getId() : a.getId(), new BigDecimal("1.00"), "swap " + i));
		});

		assertThat(outcomes).allMatch(outcome -> outcome.succeeded());
		assertThat(balanceOf(a)).isEqualByComparingTo("100.00");
		assertThat(balanceOf(b)).isEqualByComparingTo("100.00");
		assertThat(transactionCount(a)).isEqualTo(80);
	}

	@Test
	void failureAfterDebitAndCreditRollsEverythingBack() {
		User owner = newUser();
		BankAccount source = newAccount(owner, "500.00");
		BankAccount destination = newAccount(owner, "0.00");
		// Simulate a crash at the very last step - after the debit, the credit and the transaction insert.
		// Stub the spy inside Spring's transactional proxy; calling the proxy itself here would demand a
		// transaction (Propagation.MANDATORY) before Mockito even sees the call.
		AuditService spy = AopTestUtils.getUltimateTargetObject(auditService);
		doThrow(new IllegalStateException("Simulated failure while writing the audit log")).when(spy)
			.record(any(), anyString(), anyString(), anyLong(), anyMap());

		assertThatThrownBy(() -> transferService.transfer(owner.getId(),
				new TransferRequest(source.getId(), destination.getId(), new BigDecimal("200.00"), "doomed")))
			.hasMessageContaining("Simulated failure");

		assertThat(balanceOf(source)).isEqualByComparingTo("500.00");
		assertThat(balanceOf(destination)).isEqualByComparingTo("0.00");
		assertThat(transactionCount(source)).isZero();
		// Nothing happened, so nobody may be told that it did.
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ?", Integer.class,
				owner.getId())).isZero();
		verify(sseHub, never()).onUserEvent(any());
	}

	@Test
	void simultaneousRetriesWithTheSameIdempotencyKeyMoveMoneyOnce() throws Exception {
		User owner = newUser();
		BankAccount source = newAccount(owner, "100.00");
		BankAccount destination = newAccount(owner, "0.00");
		TransferRequest request = new TransferRequest(source.getId(), destination.getId(), new BigDecimal("30.00"),
				"double click");

		// 10 copies of the same request with the same key, all at the same instant (e.g. an impatient retry loop).
		List<Outcome> outcomes = runConcurrently(10,
				i -> () -> transferService.transfer(owner.getId(), request, "same-key-" + owner.getId()));

		assertThat(outcomes).allMatch(outcome -> outcome.succeeded());
		List<String> references = outcomes.stream()
			.map(outcome -> ((TransferService.TransferResult) outcome.value()).response().reference())
			.distinct()
			.toList();
		assertThat(references).as("every caller gets the one original transfer").hasSize(1);
		assertThat(outcomes.stream().filter(o -> !((TransferService.TransferResult) o.value()).replayed()).count())
			.as("exactly one request did the work; the rest were replays")
			.isEqualTo(1);
		assertThat(balanceOf(source)).isEqualByComparingTo("70.00");
		assertThat(balanceOf(destination)).isEqualByComparingTo("30.00");
		assertThat(transactionCount(source)).isEqualTo(1);
	}

	@Test
	void aFailedAttemptDoesNotUseUpItsIdempotencyKey() {
		User owner = newUser();
		BankAccount source = newAccount(owner, "10.00");
		BankAccount destination = newAccount(owner, "0.00");
		TransferRequest tooMuch = new TransferRequest(source.getId(), destination.getId(), new BigDecimal("50.00"),
				null);

		// The key is stored in the same database transaction as the transfer, so a rejected transfer leaves no
		// trace: retrying gives the real answer again, not a stuck "key already used".
		for (int attempt = 0; attempt < 2; attempt++) {
			assertThatThrownBy(() -> transferService.transfer(owner.getId(), tooMuch, "failing-key-" + owner.getId()))
				.isInstanceOfSatisfying(ApiException.class,
						ex -> assertThat(ex.getCode()).isEqualTo("INSUFFICIENT_FUNDS"));
		}
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_keys WHERE user_id = ?",
				Integer.class, owner.getId()))
			.isZero();
	}

	@Test
	void aCommittedTransferIsPushedLiveToTheReceiver() {
		User sender = newUser();
		User receiver = newUser();
		BankAccount source = newAccount(sender, "50.00");
		BankAccount destination = newAccount(receiver, "0.00");

		transferService.transfer(sender.getId(),
				new TransferRequest(source.getId(), destination.getId(), new BigDecimal("5.00"), "live"));

		// Delivered after commit, to the receiver, as a "notification" event.
		verify(sseHub, timeout(2000)).onUserEvent(org.mockito.ArgumentMatchers.argThat((LiveEvents.ForUsers event) ->
				event.userIds().contains(receiver.getId()) && event.name().equals("notification")));
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ?", Integer.class,
				receiver.getId())).isEqualTo(1);
	}

	@AfterEach
	void cleanUp() {
		for (Long userId : createdUserIds) {
			jdbcTemplate.update("DELETE FROM idempotency_keys WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM audit_logs WHERE actor_user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM transactions WHERE created_by = ?", userId);
			jdbcTemplate.update("DELETE FROM bank_accounts WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
		}
	}

	// ---- helpers -------------------------------------------------------------------------------

	private User newUser() {
		User user = transactionTemplate.execute(status -> new TestFixtures(entityManager)
			.user("Race " + UUID.randomUUID().toString().substring(0, 8), Role.CUSTOMER));
		createdUserIds.add(user.getId());
		return user;
	}

	private BankAccount newAccount(User owner, String balance) {
		String number = "6" + String.format("%011d", ThreadLocalRandom.current().nextLong(100_000_000_000L));
		return transactionTemplate.execute(status -> new TestFixtures(entityManager)
			.fundedAccount(entityManager.merge(owner), number, balance));
	}

	private BigDecimal balanceOf(BankAccount account) {
		return jdbcTemplate.queryForObject("SELECT balance FROM bank_accounts WHERE id = ?", BigDecimal.class,
				account.getId());
	}

	private int transactionCount(BankAccount account) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM transactions WHERE source_account_id = ? OR destination_account_id = ?",
				Integer.class, account.getId(), account.getId());
	}

	/** Starts all tasks at the same instant (start gate) and collects what happened to each. */
	private static List<Outcome> runConcurrently(int count, TaskFactory factory) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(count);
		CountDownLatch startGate = new CountDownLatch(1);
		try {
			List<Future<Outcome>> futures = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				Callable<?> task = factory.create(i);
				futures.add(pool.submit(() -> {
					startGate.await();
					try {
						return new Outcome(null, task.call());
					}
					catch (ApiException ex) {
						return new Outcome(ex.getCode(), null);
					}
					catch (Exception ex) {
						return new Outcome("UNEXPECTED: " + ex, null);
					}
				}));
			}
			startGate.countDown();
			List<Outcome> outcomes = new ArrayList<>();
			for (Future<Outcome> future : futures) {
				outcomes.add(future.get(60, TimeUnit.SECONDS));
			}
			return outcomes;
		}
		finally {
			pool.shutdownNow();
		}
	}

	@FunctionalInterface
	private interface TaskFactory {

		Callable<?> create(int index);

	}

	/** errorCode is null on success; value is whatever the task returned. */
	private record Outcome(String errorCode, Object value) {

		boolean succeeded() {
			return errorCode == null;
		}

	}

}
