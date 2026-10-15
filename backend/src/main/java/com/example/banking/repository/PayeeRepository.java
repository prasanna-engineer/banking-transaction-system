package com.example.banking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.banking.entity.Payee;

public interface PayeeRepository extends JpaRepository<Payee, Long> {

	List<Payee> findByUserIdOrderByNicknameAsc(Long userId);

	Optional<Payee> findByIdAndUserId(Long id, Long userId);

	boolean existsByUserIdAndAccountNumber(Long userId, String accountNumber);

}
