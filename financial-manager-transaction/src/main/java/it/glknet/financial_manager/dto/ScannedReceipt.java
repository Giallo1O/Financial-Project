package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Cio' che l'AI legge da uno scontrino; la bozza viene controllata dall'utente prima di salvare. */
public record ScannedReceipt(
		@JsonPropertyDescription("Insegna del negozio senza indirizzo (es. Esselunga, Coop, Lidl)") String storeName,
		@JsonPropertyDescription("Data dell'acquisto in formato yyyy-MM-dd; stringa vuota se non leggibile") String purchaseDate,
		@JsonPropertyDescription("Totale pagato in euro, dopo gli sconti") BigDecimal total,
		@JsonPropertyDescription("Articoli nell'ordine dello scontrino") List<ReceiptItemDto> items,
		@JsonPropertyDescription("Righe illeggibili o dubbi sulla lettura; stringa vuota se tutto e' chiaro") String warnings) {
}
