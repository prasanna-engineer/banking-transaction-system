package com.bank.banking.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bank.banking.entity.Account;
import com.bank.banking.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

	Page<Transaction> findByAccountOrderByCreatedAtDescIdDesc(Account account, Pageable pageable);

}
