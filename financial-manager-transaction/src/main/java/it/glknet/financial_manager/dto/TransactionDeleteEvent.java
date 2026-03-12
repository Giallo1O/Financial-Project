package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionDeleteEvent(UUID id, BigDecimal amount, String type, UUID categoryId, String userId, int month, int year) {
}