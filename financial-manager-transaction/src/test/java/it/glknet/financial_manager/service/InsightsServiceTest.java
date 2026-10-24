package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import it.glknet.financial_manager.dto.GroupViews.GroupView;
import it.glknet.financial_manager.dto.Insights.Alert;
import it.glknet.financial_manager.dto.Insights.Forecast;
import it.glknet.financial_manager.model.Category;
import it.glknet.financial_manager.model.RecurringTransaction;
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.model.TransactionType;
import it.glknet.financial_manager.repository.CategoryRepository;
import it.glknet.financial_manager.repository.RecurringTransactionRepository;
import it.glknet.financial_manager.repository.TransactionRepository;

class InsightsServiceTest {

	private final TransactionRepository transactions = mock(TransactionRepository.class);
	private final CategoryRepository categories = mock(CategoryRepository.class);
	private final RecurringTransactionRepository recurring = mock(RecurringTransactionRepository.class);
	private final SharedExpenseService groups = mock(SharedExpenseService.class);
	private final InsightsService service = new InsightsService(transactions, categories, recurring, groups);
	private final UUID user = UUID.randomUUID();
	private final LocalDate today = LocalDate.of(2026, 10, 10); // 31 giorni: 10 passati, 21 da fare

	private Category category(String name, String budget, boolean fixed) {
		return Category.builder().id(UUID.randomUUID()).userId(user).name(name).expense(true).fixed(fixed)
				.budget(new BigDecimal(budget)).build();
	}

	private Transaction spend(Category c, String amount, int day) {
		return Transaction.builder().userId(user).type(TransactionType.USCITA).amount(new BigDecimal(amount))
				.categoryId(c == null ? null : c.getId()).transactionDate(LocalDate.of(2026, 10, day)).build();
	}

	private Transaction income(String amount, int day) {
		return Transaction.builder().userId(user).type(TransactionType.ENTRATA).amount(new BigDecimal(amount))
				.transactionDate(LocalDate.of(2026, 10, day)).build();
	}

	private void given(List<Category> cats, List<Transaction> txs, List<RecurringTransaction> rec) {
		when(categories.findByUserId(user)).thenReturn(cats);
		when(transactions.findByUserIdAndTransactionDateBetween(any(), any(), any())).thenReturn(txs);
		when(recurring.findByUserIdAndActiveTrueOrderByDayOfMonthAsc(user)).thenReturn(rec);
		when(groups.groups(user)).thenReturn(List.of());
	}

	private RecurringTransaction rent(String amount, LocalDate due, boolean auto, TransactionType type) {
		return RecurringTransaction.builder().id(UUID.randomUUID()).userId(user).description("Affitto")
				.amount(new BigDecimal(amount)).type(type).nextDueDate(due).autoConfirm(auto).build();
	}

	@Test
	void forecastExtrapolatesOnlyVariableSpendingAndAddsPendingRecurring() {
		Category fixed = category("Casa", "0", true);
		Category food = category("Spesa", "0", false);
		// 400 speso: 100 su categoria fissa, 300 su variabile; entrata 1000
		given(List.of(fixed, food), List.of(income("1000", 1), spend(fixed, "100", 2), spend(food, "300", 5)),
				List.of(rent("100", LocalDate.of(2026, 10, 28), false, TransactionType.USCITA)));

		Forecast f = service.forecast(user, today);

		assertEquals(21, f.daysLeft());
		assertTrue(f.reliable());
		assertEquals(0, new BigDecimal("30").compareTo(f.dailyVariable()));
		assertEquals(0, new BigDecimal("630").compareTo(f.projectedVariable()));
		assertEquals(0, new BigDecimal("1000").compareTo(f.projectedIncome()));
		assertEquals(0, new BigDecimal("1130").compareTo(f.projectedExpenses())); // 400 + 100 in arrivo + 630
		assertEquals(0, new BigDecimal("-130").compareTo(f.projectedBalance()));
	}

	@Test
	void forecastIsMarkedUnreliableInTheFirstDays() {
		given(List.of(), List.of(), List.of());
		assertFalse(service.forecast(user, LocalDate.of(2026, 10, 2)).reliable());
	}

	@Test
	void futureDatedSpendingCountsAsKnownButNotAsPace() {
		Category food = category("Spesa", "0", false);
		given(List.of(food), List.of(spend(food, "50", 20)), List.of());

		Forecast f = service.forecast(user, today);

		assertEquals(0, new BigDecimal("50").compareTo(f.expensesSoFar()));
		assertEquals(0, BigDecimal.ZERO.compareTo(f.projectedVariable()));
	}

	@Test
	void budgetAlertsGoFromOverToNearToPaceAndAreSortedBySeverity() {
		Category over = category("Svago", "100", false);
		Category near = category("Spesa", "300", false);
		Category pace = category("Benzina", "300", false);
		Category fine = category("Altro", "500", false);
		// a meta' mese (10 su 31): Benzina 100 -> proiezione 310 > 300; Altro 10 -> nessun avviso
		given(List.of(fine, pace, near, over),
				List.of(spend(over, "120", 3), spend(near, "250", 4), spend(pace, "100", 5), spend(fine, "10", 6),
						income("5000", 1)),
				List.of());

		List<Alert> alerts = service.alerts(user, today);

		assertEquals(List.of("BUDGET_OVER", "BUDGET_NEAR", "BUDGET_PACE"), alerts.stream().map(Alert::type).toList());
		assertEquals(List.of("danger", "warning", "info"), alerts.stream().map(Alert::severity).toList());
		assertTrue(alerts.get(0).message().contains("Svago"));
	}

	@Test
	void noBudgetNoAlert() {
		Category free = category("Svago", "0", false);
		given(List.of(free), List.of(spend(free, "999", 3), income("5000", 1)), List.of());
		assertTrue(service.alerts(user, today).isEmpty());
	}

	@Test
	void recurringDueSoonOrOverdueIsReportedButAutoConfirmedAreNot() {
		given(List.of(), List.of(income("5000", 1)), List.of(
				rent("700", LocalDate.of(2026, 10, 12), false, TransactionType.USCITA),
				rent("50", LocalDate.of(2026, 10, 8), false, TransactionType.USCITA),
				rent("10", LocalDate.of(2026, 10, 11), true, TransactionType.USCITA),
				rent("20", LocalDate.of(2026, 10, 25), false, TransactionType.USCITA)));

		List<Alert> alerts = service.alerts(user, today);

		assertEquals(List.of("RECURRING_OVERDUE", "RECURRING_DUE"), alerts.stream().map(Alert::type).toList());
	}

	@Test
	void redForecastAndGroupBalancesAreReported() {
		Category food = category("Spesa", "0", false);
		given(List.of(food), List.of(income("100", 1), spend(food, "300", 5)), List.of());
		when(groups.groups(user)).thenReturn(List.of(
				new GroupView(UUID.randomUUID(), "Casa", null, List.of(), new BigDecimal("-45.00")),
				new GroupView(UUID.randomUUID(), "Viaggio", null, List.of(), new BigDecimal("10.00")),
				new GroupView(UUID.randomUUID(), "Pari", null, List.of(), BigDecimal.ZERO)));

		List<Alert> alerts = service.alerts(user, today);

		assertEquals(List.of("FORECAST_RED", "GROUP_DEBT", "GROUP_CREDIT"), alerts.stream().map(Alert::type).toList());
		assertTrue(alerts.get(1).message().contains("45,00"));
	}
}
