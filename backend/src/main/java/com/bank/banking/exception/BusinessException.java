package com.bank.banking.exception;

/** A request that is well-formed but breaks a banking rule (e.g. insufficient funds). */
public class BusinessException extends RuntimeException {

	public BusinessException(String message) {
		super(message);
	}

}
