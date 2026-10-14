package com.example.banking.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.example.banking.entity.BankAccount;
import com.example.banking.entity.User;
import com.example.banking.enums.AccountStatus;
import com.example.banking.enums.AccountType;
import com.example.banking.enums.Role;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class BankAccountRepositoryTest {

	@Autowired
	private BankAccountRepository accountRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private User alice;

	private User bob;

	private BankAccount aliceSavings;

	private BankAccount bobSavings;

	@BeforeEach
	void setUp() {
		alice = entityManager.persist(new User("Alice Test", "alice@example.com", "hash", Role.CUSTOMER));
		bob = entityManager.persist(new User("Bob Test", "bob@example.com", "hash", Role.CUSTOMER));
		aliceSavings = entityManager.persist(new BankAccount(alice, "900000000001", AccountType.SAVINGS, "INR"));
		entityManager.persist(new BankAccount(alice, "900000000002", AccountType.CURRENT, "INR"));
		bobSavings = entityManager.persist(new BankAccount(bob, "900000000003", AccountType.SAVINGS, "INR"));
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	void findsOnlyAccountsBelongingToTheGivenUser() {
		assertThat(accountRepository.findByUserIdOrderByIdAsc(alice.getId()))
			.extracting(BankAccount::getAccountNumber)
			.containsExactly("900000000001", "900000000002");
	}

	@Test
	void anotherUsersAccountIsNotFoundByOwnershipQuery() {
		assertThat(accountRepository.findByIdAndUserId(bobSavings.getId(), alice.getId())).isEmpty();
		assertThat(accountRepository.findByIdAndUserId(bobSavings.getId(), bob.getId())).isPresent();
	}

	@Test
	void newAccountStartsActiveWithZeroBalanceAtTwoDecimalPlaces() {
		BankAccount loaded = accountRepository.findByAccountNumber("900000000001").orElseThrow();

		assertThat(loaded.getBalance()).isEqualByComparingTo("0.00");
		assertThat(loaded.getBalance().scale()).isEqualTo(2);
		assertThat(loaded.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(loaded.getVersion()).isZero();
	}

	@Test
	void databaseRejectsNegativeBalance() {
		// Bypass Java entirely: even buggy code or a manual SQL update cannot store a negative balance.
		assertThatThrownBy(() -> jdbcTemplate.update("UPDATE bank_accounts SET balance = -1.00 WHERE id = ?",
				aliceSavings.getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
