package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Un conto con il suo saldo attuale (saldo iniziale + entrate - uscite + giroconti, fino a oggi). */
public record AccountView(UUID id, String name, String type, String color, BigDecimal initialBalance,
		BigDecimal balance, boolean archived) {
}
