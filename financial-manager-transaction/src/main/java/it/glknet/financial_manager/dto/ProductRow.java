package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una riga prodotto con data e negozio dello scontrino (risultato della query sui prodotti). */
public record ProductRow(String name, String unit, String foodCategory, BigDecimal quantity, BigDecimal unitPrice,
		BigDecimal totalPrice, LocalDate purchaseDate, String storeName) {

	/**
	 * Prezzo davvero pagato per unita', sconto compreso (importo della riga / quantita'). Null per le righe di solo
	 * sconto o senza prezzo: non sono prodotti.
	 */
	public BigDecimal paidUnitPrice() {
		if (totalPrice != null && quantity != null && quantity.signum() > 0) {
			return totalPrice.signum() > 0 ? totalPrice.divide(quantity, 4, java.math.RoundingMode.HALF_UP) : null;
		}
		return unitPrice != null && unitPrice.signum() > 0 ? unitPrice : null;
	}
}
