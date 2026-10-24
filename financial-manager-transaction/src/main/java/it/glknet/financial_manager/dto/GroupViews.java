package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Risposte dell'API dei gruppi di spese condivise. */
public final class GroupViews {

	private GroupViews() {
	}

	public record Member(UUID userId, String username) {
	}

	/** myBalance > 0: gli altri ti devono dei soldi; < 0: li devi tu. */
	public record GroupView(UUID id, String name, String inviteCode, List<Member> members, BigDecimal myBalance) {
	}

	public record Balance(UUID userId, BigDecimal amount) {
	}

	/** "from" deve dare "amount" a "to" per pareggiare i conti. */
	public record Debt(UUID from, UUID to, BigDecimal amount) {
	}

	public record Share(UUID userId, BigDecimal amount) {
	}

	public record ExpenseView(UUID id, String description, BigDecimal amount, UUID paidBy, LocalDate date,
			List<Share> shares) {
	}

	public record SettlementView(UUID id, UUID from, UUID to, BigDecimal amount, LocalDate date) {
	}

	public record Summary(GroupView group, List<Balance> balances, List<Debt> debts, List<ExpenseView> expenses,
			List<SettlementView> settlements) {
	}
}
