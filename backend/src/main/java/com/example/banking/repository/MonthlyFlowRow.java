package com.example.banking.repository;

import java.math.BigDecimal;

/** One row of the monthly money-in / money-out query (a Spring Data "projection"). */
public interface MonthlyFlowRow {

	/** "2026-10" */
	String getYm();

	BigDecimal getMoneyIn();

	BigDecimal getMoneyOut();

}
