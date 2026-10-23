package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un prodotto che compri di solito. due = e' passato abbastanza tempo dall'ultimo acquisto, rispetto a ogni quanti
 * giorni lo compri in media, da averne probabilmente bisogno.
 */
public record ShoppingSuggestion(String name, String unit, String foodCategory, int purchases, LocalDate lastDate,
		Integer avgIntervalDays, long daysSinceLast, boolean due, BigDecimal lastUnitPrice, BigDecimal minUnitPrice,
		String cheapestStore) {
}
