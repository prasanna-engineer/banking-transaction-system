package com.example.banking.dto.insights;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Months are oldest first; every month in the range is present (zero when nothing moved). */
public record MonthlyFlowResponse(String currency, List<MonthFlow> months) {

	public record MonthFlow(String month, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal moneyIn,
			@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal moneyOut) {
	}

}
