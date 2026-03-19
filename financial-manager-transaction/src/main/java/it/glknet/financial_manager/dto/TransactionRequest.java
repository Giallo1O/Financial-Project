package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import it.glknet.financial_manager.model.TransactionType;

public record TransactionRequest(String description, BigDecimal amount, TransactionType type, UUID categoryId,
		UUID userId, LocalDate transactionDate) {

}
