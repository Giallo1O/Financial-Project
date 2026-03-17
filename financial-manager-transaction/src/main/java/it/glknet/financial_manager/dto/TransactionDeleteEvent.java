package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionDeleteEvent(UUID id, BigDecimal amount, String type, UUID categoryId,UUID userId,
                LocalDate transactionDate) {
}