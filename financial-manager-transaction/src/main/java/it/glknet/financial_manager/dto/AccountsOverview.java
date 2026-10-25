package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * unassigned: saldo dei movimenti che non hanno un conto (dati precedenti ai conti); hasUnassigned dice se ce ne sono.
 * total: patrimonio = somma dei saldi di tutti i conti + unassigned.
 */
public record AccountsOverview(List<AccountView> accounts, BigDecimal unassigned, boolean hasUnassigned,
		BigDecimal total) {
}
