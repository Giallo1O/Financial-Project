package it.glknet.financial_manager.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

import it.glknet.financial_manager.dto.Insights.Forecast;

/** Il calcolo della previsione, separato dal database per poterlo provare con numeri a mano. */
final class ForecastMath {

	/** Prima di questo giorno del mese il ritmo di spesa e' troppo poco significativo. */
	static final int MIN_DAYS_FOR_PACE = 3;

	private ForecastMath() {
	}

	static Forecast forecast(LocalDate today, BigDecimal incomeSoFar, BigDecimal expensesSoFar,
			BigDecimal variableSpentToDate, BigDecimal pendingIncome, BigDecimal pendingExpenses) {
		YearMonth month = YearMonth.from(today);
		int elapsed = today.getDayOfMonth();
		int left = month.lengthOfMonth() - elapsed;
		BigDecimal daily = variableSpentToDate.divide(BigDecimal.valueOf(elapsed), 2, RoundingMode.HALF_UP);
		BigDecimal projectedVariable = variableSpentToDate.multiply(BigDecimal.valueOf(left))
				.divide(BigDecimal.valueOf(elapsed), 2, RoundingMode.HALF_UP);
		BigDecimal projectedIncome = incomeSoFar.add(pendingIncome);
		BigDecimal projectedExpenses = expensesSoFar.add(pendingExpenses).add(projectedVariable);
		return new Forecast(month.toString(), today, left, elapsed >= MIN_DAYS_FOR_PACE, incomeSoFar, expensesSoFar,
				pendingIncome, pendingExpenses, daily, projectedVariable, projectedIncome, projectedExpenses,
				projectedIncome.subtract(projectedExpenses));
	}

	/** A questo ritmo, quanto avra' speso una categoria a fine mese. */
	static BigDecimal paceProjection(LocalDate today, BigDecimal spentToDate) {
		return spentToDate.multiply(BigDecimal.valueOf(YearMonth.from(today).lengthOfMonth()))
				.divide(BigDecimal.valueOf(today.getDayOfMonth()), 2, RoundingMode.HALF_UP);
	}
}
