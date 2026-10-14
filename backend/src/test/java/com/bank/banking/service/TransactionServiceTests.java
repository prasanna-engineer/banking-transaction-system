package com.bank.banking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.bank.banking.dto.AccountResponse;
import com.bank.banking.dto.CreateAccountRequest;
import com.bank.banking.dto.TransferRequest;
import com.bank.banking.entity.AccountStatus;
import com.bank.banking.entity.AccountType;
import com.bank.banking.entity.TransactionType;
import com.bank.banking.exception.BusinessException;
import com.bank.banking.exception.ResourceNotFoundException;

@SpringBootTest
@Transactional // each test is rolled back afterwards, so tests don't affect each other
class TransactionServiceTests {

	@Autowired
	private AccountService accountService;

	@Autowired
	private TransactionService transactionService;

	@Test
	void createAccountWithInitialDepositRecordsTransaction() {
		AccountResponse account = open("500.00");

		assertThat(account.accountNumber()).hasSize(12).startsWith("10");
		assertThat(account.balance()).isEqualByComparingTo("500.00");
		var history = transactionService.history(account.accountNumber(), 0, 10);
		assertThat(history.totalElements()).isEqualTo(1);
		assertThat(history.content().get(0).type()).isEqualTo(TransactionType.DEPOSIT);
	}

	@Test
	void depositAndWithdrawUpdateBalance() {
		String number = open("100.00").accountNumber();

		transactionService.deposit(number, new BigDecimal("50.25"), null);
		var withdrawal = transactionService.withdraw(number, new BigDecimal("30.25"), "ATM");

		assertThat(withdrawal.balanceAfter()).isEqualByComparingTo("120.00");
		assertThat(accountService.findByNumber(number).balance()).isEqualByComparingTo("120.00");
	}

	@Test
	void withdrawMoreThanBalanceIsRejected() {
		String number = open("100.00").accountNumber();

		assertThatThrownBy(() -> transactionService.withdraw(number, new BigDecimal("100.01"), null))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("Insufficient funds");
	}

	@Test
	void transferMovesMoneyAndWritesTwoLedgerLines() {
		String from = open("1000.00").accountNumber();
		String to = open("0").accountNumber();

		var result = transactionService.transfer(new TransferRequest(from, to, new BigDecimal("250.00"), "Rent"));

		assertThat(result.fromBalanceAfter()).isEqualByComparingTo("750.00");
		assertThat(accountService.findByNumber(to).balance()).isEqualByComparingTo("250.00");
		var incoming = transactionService.history(to, 0, 10).content().get(0);
		assertThat(incoming.type()).isEqualTo(TransactionType.TRANSFER_IN);
		assertThat(incoming.reference()).isEqualTo(result.reference());
		assertThat(incoming.counterpartyAccountNumber()).isEqualTo(from);
	}

	@Test
	void transferToSameAccountIsRejected() {
		String number = open("100.00").accountNumber();

		assertThatThrownBy(() -> transactionService
			.transfer(new TransferRequest(number, number, new BigDecimal("10.00"), null)))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	void unknownAccountIsNotFound() {
		assertThatThrownBy(() -> transactionService.deposit("999999999999", BigDecimal.TEN, null))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void closedAccountCannotReceiveMoney() {
		String number = open("0").accountNumber();
		assertThat(accountService.close(number).status()).isEqualTo(AccountStatus.CLOSED);

		assertThatThrownBy(() -> transactionService.deposit(number, BigDecimal.TEN, null))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("closed");
	}

	@Test
	void accountWithBalanceCannotBeClosed() {
		String number = open("1.00").accountNumber();

		assertThatThrownBy(() -> accountService.close(number)).isInstanceOf(BusinessException.class);
	}

	private AccountResponse open(String initialDeposit) {
		return accountService.create(new CreateAccountRequest("Test User", "test@example.com", AccountType.SAVINGS,
				new BigDecimal(initialDeposit)));
	}

}
