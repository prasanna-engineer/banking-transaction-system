package com.example.banking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.dto.insights.MonthlyFlowResponse;
import com.example.banking.dto.insights.MonthlyFlowResponse.MonthFlow;
import com.example.banking.entity.User;
import com.example.banking.exception.ApiException;
import com.example.banking.repository.BankTransactionRepository;
import com.example.banking.repository.MonthlyFlowRow;

@Service
public class InsightsService {

	static final int MAX_MONTHS = 12;

	private static final BigDecimal ZERO = new BigDecimal("0.00");

	private final BankTransactionRepository transactionRepository;

	private final CurrentUserService currentUserService;

	private final Clock clock;

	public InsightsService(BankTransactionRepository transactionRepository, CurrentUserService currentUserService) {
		this.transactionRepository = transactionRepository;
		this.currentUserService = currentUserService;
		this.clock = Clock.systemUTC();
	}

	/** The last {@code months} calendar months including the current one, oldest first. */
	@Transactional(readOnly = true)
	public MonthlyFlowResponse monthlyFlow(Long currentUserId, int months, String currency) {
		if (months < 1 || months > MAX_MONTHS) {
			throw ApiException.badRequest("INVALID_PARAMETER", "'months' must be between 1 and " + MAX_MONTHS + ".");
		}
		User user = currentUserService.requireActiveUser(currentUserId);
		YearMonth current = YearMonth.now(clock);
		YearMonth first = current.minusMonths(months - 1L);

		Map<String, MonthlyFlowRow> rows = transactionRepository
			.monthlyFlow(user.getId(), currency, first.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC))
			.stream()
			.collect(Collectors.toMap(row -> row.getYm(), Function.identity()));

		// Fill gaps so the chart always shows every month, even quiet ones.
		List<MonthFlow> result = new ArrayList<>();
		for (YearMonth month = first; !month.isAfter(current); month = month.plusMonths(1)) {
			MonthlyFlowRow row = rows.get(month.toString());
			result.add(new MonthFlow(month.toString(), row == null ? ZERO : money(row.getMoneyIn()),
					row == null ? ZERO : money(row.getMoneyOut())));
		}
		return new MonthlyFlowResponse(currency, result);
	}

	private static BigDecimal money(BigDecimal value) {
		return value == null ? ZERO : value.setScale(2, RoundingMode.UNNECESSARY);
	}

}
