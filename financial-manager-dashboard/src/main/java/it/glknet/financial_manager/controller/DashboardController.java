package it.glknet.financial_manager.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import it.glknet.financial_manager.model.CategorySummary;
import it.glknet.financial_manager.repository.CategorySummaryRepository;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "http://localhost:3000") // Frontend Docker port
@RequiredArgsConstructor
public class DashboardController {

	private final CategorySummaryRepository repository;

	@GetMapping("/stats")
	public ResponseEntity<List<CategorySummary>> getStats(
			@RequestParam String userId,
			@RequestParam Integer month,
			@RequestParam Integer year) {
		return ResponseEntity.ok(repository.findByUserIdAndMonthAndYear(userId, month, year));
	}
}
