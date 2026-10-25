package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Previsione di fine mese e avvisi. */
public final class Insights {

	private Insights() {
	}

	/**
	 * Previsione del mese in corso. "Già registrato" comprende anche i movimenti con data futura nel mese;
	 * "in arrivo" sono le ricorrenze non ancora confermate; la spesa variabile resta da stimare dal ritmo di
	 * spesa dei giorni passati nelle categorie non fisse. {@code reliable} = false nei primi giorni del mese.
	 */
	public record Forecast(String month, LocalDate today, int daysLeft, boolean reliable, BigDecimal incomeSoFar,
			BigDecimal expensesSoFar, BigDecimal pendingIncome, BigDecimal pendingExpenses, BigDecimal dailyVariable,
			BigDecimal projectedVariable, BigDecimal projectedIncome, BigDecimal projectedExpenses,
			BigDecimal projectedBalance) {
	}

	/** severity: danger | warning | info; link: pagina dell'app dove agire. */
	public record Alert(String type, String severity, String message, String link) {
	}
}
