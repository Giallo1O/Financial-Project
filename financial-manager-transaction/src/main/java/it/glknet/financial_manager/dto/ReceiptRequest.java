package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Scontrino controllato dall'utente, da salvare come spesa + righe prodotto. */
public record ReceiptRequest(String storeName, LocalDate purchaseDate, BigDecimal total, UUID categoryId,
		List<ReceiptItemDto> items) {
}
