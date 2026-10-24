package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Scontrino controllato dall'utente, da salvare come spesa + righe prodotto.
 * accountId: il conto su cui registrare la spesa (se non si collega a un movimento esistente).
 * Se linkTransactionId e' indicato, lo scontrino si collega a quel movimento (es. gia' importato dalla banca) invece di creare una nuova spesa.
 */
public record ReceiptRequest(String storeName, LocalDate purchaseDate, BigDecimal total, UUID categoryId,
		List<ReceiptItemDto> items, UUID linkTransactionId, UUID accountId) {

	public ReceiptRequest(String storeName, LocalDate purchaseDate, BigDecimal total, UUID categoryId,
			List<ReceiptItemDto> items, UUID linkTransactionId) {
		this(storeName, purchaseDate, total, categoryId, items, linkTransactionId, null);
	}
}
