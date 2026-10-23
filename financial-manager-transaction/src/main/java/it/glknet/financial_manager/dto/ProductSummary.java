package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductSummary(String name, String unit, String foodCategory, int purchases, BigDecimal lastUnitPrice,
		BigDecimal minUnitPrice, BigDecimal avgUnitPrice, BigDecimal maxUnitPrice, LocalDate lastDate,
		String lastStore, String cheapestStore) {
}
