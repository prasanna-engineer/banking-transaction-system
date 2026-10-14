package com.example.banking.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.example.banking.entity.User;
import com.example.banking.enums.Role;
import com.example.banking.enums.UserStatus;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void savesUserAndFindsItByNormalizedEmail() {
		userRepository.save(new User("  Test Customer ", "  Test.Customer@Example.COM ", "hash", Role.CUSTOMER));
		// Write to MariaDB and forget the cached object, so the next read really hits the database.
		entityManager.flush();
		entityManager.clear();

		User found = userRepository.findByEmail("test.customer@example.com").orElseThrow();

		assertThat(found.getId()).isNotNull();
		assertThat(found.getFullName()).isEqualTo("Test Customer");
		assertThat(found.getRole()).isEqualTo(Role.CUSTOMER);
		assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(userRepository.existsByEmail("test.customer@example.com")).isTrue();
	}

	@Test
	void duplicateEmailIsRejectedByUniqueConstraint() {
		userRepository.saveAndFlush(new User("First", "dup@example.com", "hash", Role.CUSTOMER));

		assertThatThrownBy(
				() -> userRepository.saveAndFlush(new User("Second", "DUP@example.com", "hash", Role.CUSTOMER)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
