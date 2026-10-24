package it.glknet.financial_manager.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.glknet.financial_manager.config.AuthInterceptor;
import it.glknet.financial_manager.dto.Insights.Alert;
import it.glknet.financial_manager.dto.Insights.Forecast;
import it.glknet.financial_manager.service.InsightsService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InsightsController {

	private final InsightsService service;

	@GetMapping("/forecast")
	public Forecast forecast(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
		return service.forecast(userId);
	}

	@GetMapping("/alerts")
	public List<Alert> alerts(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
		return service.alerts(userId);
	}
}
