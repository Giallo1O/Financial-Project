package it.glknet.financial_manager.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

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
import lombok.RequiredArgsConstructor;

/** Previsione di fine mese e avvisi (budget, scadenze, debiti con gli altri). */
@Service
@RequiredArgsConstructor
public class InsightsService {

	private static final BigDecimal NEAR = new BigDecimal("0.80");
	private static final int DUE_SOON_DAYS = 3;
	private static final List<String> SEVERITY_ORDER = List.of("danger", "warning", "info");

	private final TransactionRepository transactions;
	private final CategoryRepository categories;
	private final RecurringTransactionRepository recurring;
	private final SharedExpenseService groups;

	public Forecast forecast(UUID userId) {
		return forecast(userId, LocalDate.now());
	}

	public Forecast forecast(UUID userId, LocalDate today) {
		YearMonth month = YearMonth.from(today);
		List<Transaction> monthTx = transactions.findByUserIdAndTransactionDateBetween(userId, month.atDay(1),
				month.atEndOfMonth());
		Map<UUID, Category> cats = categoryMap(userId);

		BigDecimal income = BigDecimal.ZERO;
		BigDecimal expenses = BigDecimal.ZERO;
		BigDecimal variable = BigDecimal.ZERO;
		for (Transaction t : monthTx) {
			if (t.getType() == TransactionType.ENTRATA) {
				income = income.add(t.getAmount());
			} else {
				expenses = expenses.add(t.getAmount());
				Category c = t.getCategoryId() == null ? null : cats.get(t.getCategoryId());
				if (!t.getTransactionDate().isAfter(today) && (c == null || !c.isFixed())) {
					variable = variable.add(t.getAmount());
				}
			}
		}
		BigDecimal pendingIncome = BigDecimal.ZERO;
		BigDecimal pendingExpenses = BigDecimal.ZERO;
		for (RecurringTransaction r : dueThisMonth(userId, month)) {
			if (r.getType() == TransactionType.ENTRATA) {
				pendingIncome = pendingIncome.add(r.getAmount());
			} else {
				pendingExpenses = pendingExpenses.add(r.getAmount());
			}
		}
		return ForecastMath.forecast(today, income, expenses, variable, pendingIncome, pendingExpenses);
	}

	public List<Alert> alerts(UUID userId) {
		return alerts(userId, LocalDate.now());
	}

	public List<Alert> alerts(UUID userId, LocalDate today) {
		List<Alert> out = new ArrayList<>();
		YearMonth month = YearMonth.from(today);
		List<Transaction> monthTx = transactions.findByUserIdAndTransactionDateBetween(userId, month.atDay(1),
				month.atEndOfMonth());

		for (Category c : categories.findByUserId(userId)) {
			if (!c.isExpense() || c.getBudget() == null || c.getBudget().signum() <= 0) {
				continue;
			}
			BigDecimal spent = monthTx.stream()
					.filter(t -> t.getType() == TransactionType.USCITA && c.getId().equals(t.getCategoryId()))
					.map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
			if (spent.compareTo(c.getBudget()) >= 0) {
				out.add(new Alert("BUDGET_OVER", "danger", "Budget «" + c.getName() + "» superato: " + eur(spent)
						+ " su " + eur(c.getBudget()), "/budgets"));
			} else if (spent.compareTo(c.getBudget().multiply(NEAR)) >= 0) {
				out.add(new Alert("BUDGET_NEAR", "warning", "Budget «" + c.getName() + "» quasi finito: " + eur(spent)
						+ " su " + eur(c.getBudget()), "/budgets"));
			} else if (today.getDayOfMonth() >= ForecastMath.MIN_DAYS_FOR_PACE) {
				BigDecimal pace = ForecastMath.paceProjection(today, spent);
				if (pace.compareTo(c.getBudget()) > 0) {
					out.add(new Alert("BUDGET_PACE", "info", "Di questo passo «" + c.getName() + "» chiude a " + eur(pace)
							+ " su " + eur(c.getBudget()), "/budgets"));
				}
			}
		}

		for (RecurringTransaction r : recurring.findByUserIdAndActiveTrueOrderByDayOfMonthAsc(userId)) {
			LocalDate due = r.getNextDueDate();
			if (r.isAutoConfirm() || due == null || due.isAfter(today.plusDays(DUE_SOON_DAYS))) {
				continue;
			}
			String what = "«" + r.getDescription() + "» (" + eur(r.getAmount()) + ")";
			if (due.isBefore(today)) {
				out.add(new Alert("RECURRING_OVERDUE", "warning",
						what + " era in scadenza il " + date(due) + ": confermala", "/recurring"));
			} else {
				out.add(new Alert("RECURRING_DUE", "info", what + " scade il " + date(due), "/recurring"));
			}
		}

		Forecast f = forecast(userId, today);
		if (f.reliable() && f.projectedBalance().signum() < 0) {
			out.add(new Alert("FORECAST_RED", "danger",
					"Di questo passo il mese chiude in rosso di " + eur(f.projectedBalance().negate()), "/dashboard"));
		}

		for (GroupView g : groups.groups(userId)) {
			if (g.myBalance().signum() < 0) {
				out.add(new Alert("GROUP_DEBT", "info", "In «" + g.name() + "» devi dare " + eur(g.myBalance().negate()), "/duo"));
			} else if (g.myBalance().signum() > 0) {
				out.add(new Alert("GROUP_CREDIT", "info", "In «" + g.name() + "» ti devono " + eur(g.myBalance()), "/duo"));
			}
		}

		out.sort(Comparator.comparingInt(a -> SEVERITY_ORDER.indexOf(a.severity())));
		return out;
	}

	/** Ricorrenze attive che scadono entro fine mese (comprese quelle in ritardo): non sono ancora nei movimenti. */
	private List<RecurringTransaction> dueThisMonth(UUID userId, YearMonth month) {
		return recurring.findByUserIdAndActiveTrueOrderByDayOfMonthAsc(userId).stream()
				.filter(r -> r.getNextDueDate() != null && !r.getNextDueDate().isAfter(month.atEndOfMonth())).toList();
	}

	private Map<UUID, Category> categoryMap(UUID userId) {
		return categories.findByUserId(userId).stream().collect(java.util.stream.Collectors.toMap(Category::getId, c -> c));
	}

	private static String eur(BigDecimal n) {
		return NumberFormat.getCurrencyInstance(Locale.ITALY).format(n).replace(' ', ' ');
	}

	private static String date(LocalDate d) {
		return d.format(DateTimeFormatter.ofPattern("dd/MM"));
	}
}
