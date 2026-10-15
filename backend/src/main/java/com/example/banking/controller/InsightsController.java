package com.example.banking.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.banking.dto.insights.MonthlyFlowResponse;
import com.example.banking.security.CurrentUserId;
import com.example.banking.service.InsightsService;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Insights", description = "Summaries of your own money movements")
@RestController
@RequestMapping("/api/v1/insights")
public class InsightsController {

	private final InsightsService insightsService;

	public InsightsController(InsightsService insightsService) {
		this.insightsService = insightsService;
	}

	// GET /api/v1/insights/monthly-flow?months=6&currency=INR
	@GetMapping("/monthly-flow")
	public MonthlyFlowResponse monthlyFlow(@CurrentUserId Long currentUserId,
			@RequestParam(defaultValue = "6") int months, @RequestParam(defaultValue = "INR") String currency) {
		return insightsService.monthlyFlow(currentUserId, months, currency);
	}

}
