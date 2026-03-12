package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionEvent(UUID id, String description, BigDecimal amount, String type, UUID categoryId,
		String userId, LocalDate transactionDate) {
}