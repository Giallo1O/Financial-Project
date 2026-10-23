package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import it.glknet.financial_manager.model.TransactionType;

/**
 * Riga di anteprima dell'import: i dati letti dal file e, se esiste gia' un movimento (o uno scontrino) uguale,
 * duplicate = true con la spiegazione in duplicateNote.
 */
public record ImportRow(String description, BigDecimal amount, TransactionType type, UUID categoryId,
		LocalDate transactionDate, boolean duplicate, String duplicateNote) {
}
