package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PricePoint(LocalDate date, String store, BigDecimal unitPrice, BigDecimal quantity) {
}
