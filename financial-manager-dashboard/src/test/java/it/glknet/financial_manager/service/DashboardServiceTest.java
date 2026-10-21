package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import it.glknet.financial_manager.dto.TransactionEvent;
import it.glknet.financial_manager.model.CategorySummary;
import it.glknet.financial_manager.model.TransactionType;
import it.glknet.financial_manager.repository.CategorySummaryRepository;

class DashboardServiceTest {

	private final CategorySummaryRepository repo = mock(CategorySummaryRepository.class);
	private final DashboardService service = new DashboardService(repo);

	private CategorySummary summaryFor(TransactionType type) {
		when(repo.findByUserIdAndMonthAndYearAndExpense(any(), anyInt(), anyInt(), anyBoolean()))
				.thenReturn(Optional.empty());
		service.updateStatistics(new TransactionEvent(UUID.randomUUID(), "x", new BigDecimal("10"), type, null,
				UUID.randomUUID(), LocalDate.of(2026, 3, 1)));
		ArgumentCaptor<CategorySummary> saved = ArgumentCaptor.forClass(CategorySummary.class);
		verify(repo).save(saved.capture());
		return saved.getValue();
	}

	@Test
	void usciteAreCountedAsExpenses() {
		CategorySummary s = summaryFor(TransactionType.USCITA);
		assertTrue(s.isExpense());
		assertEquals(0, BigDecimal.TEN.compareTo(s.getTotalAmount()));
	}

	@Test
	void entrateAreNotCountedAsExpenses() {
		assertFalse(summaryFor(TransactionType.ENTRATA).isExpense());
	}
}
