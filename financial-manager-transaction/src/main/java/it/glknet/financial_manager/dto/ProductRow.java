package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una riga prodotto con data e negozio dello scontrino (risultato della query sui prodotti). */
public record ProductRow(String name, String unit, String foodCategory, BigDecimal quantity, BigDecimal unitPrice,
		BigDecimal totalPrice, LocalDate purchaseDate, String storeName) {
}
