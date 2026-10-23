package it.glknet.financial_manager.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Riga di scontrino, sia come risultato della lettura sia come richiesta di salvataggio. */
public record ReceiptItemDto(
		@JsonPropertyDescription("Testo della riga esattamente come stampato sullo scontrino") String rawName,
		@JsonPropertyDescription("Nome leggibile e normalizzato in italiano, abbreviazioni espanse, marchio mantenuto se stampato (es. 'Latte parzialmente scremato 1L')") String name,
		@JsonPropertyDescription("Una tra: Frutta e verdura, Carne e pesce, Latticini e uova, Pane e cereali, Dispensa, Bevande, Dolci e snack, Surgelati, Casa e igiene, Altro") String foodCategory,
		@JsonPropertyDescription("Numero di unita' acquistate; per articoli a peso o volume il peso/volume (es. 0.532). 1 se non indicato") BigDecimal quantity,
		@JsonPropertyDescription("Unita' di misura: pz, kg oppure l") String unit,
		@JsonPropertyDescription("Prezzo per unita' (per kg o per l negli articoli a peso/volume), in euro") BigDecimal unitPrice,
		@JsonPropertyDescription("Importo della riga in euro; negativo per sconti e coupon") BigDecimal totalPrice) {
}
