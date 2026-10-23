package it.glknet.financial_manager.dto;

import java.math.BigDecimal;

/** Prezzi unitari noti di un prodotto, ricavati dagli scontrini di chi guarda la lista. */
public record PriceHint(BigDecimal lastUnitPrice, BigDecimal minUnitPrice, String cheapestStore, String lastStore) {
}
