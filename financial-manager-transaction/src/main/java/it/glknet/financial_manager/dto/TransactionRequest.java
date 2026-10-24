package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import it.glknet.financial_manager.model.TransactionType;

/** accountId e' facoltativo: il conto su cui registrare il movimento. */
public record TransactionRequest(String description, BigDecimal amount, TransactionType type, UUID categoryId,
		UUID userId, LocalDate transactionDate, UUID accountId) {

	/** Senza conto (import da CSV, ecc.). */
	public TransactionRequest(String description, BigDecimal amount, TransactionType type, UUID categoryId,
			UUID userId, LocalDate transactionDate) {
		this(description, amount, type, categoryId, userId, transactionDate, null);
	}
}
