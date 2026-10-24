package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import it.glknet.financial_manager.dto.GroupViews.Debt;
import it.glknet.financial_manager.model.SharedExpense;
import it.glknet.financial_manager.model.SharedSettlement;
import it.glknet.financial_manager.model.SharedShare;

class SplitMathTest {

	private final UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private final UUID b = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private final UUID c = UUID.fromString("00000000-0000-0000-0000-00000000000c");

	@Test
	void splitNeverLosesACent() {
		Map<UUID, Long> parts = SplitMath.split(1000, List.of(a, b, c));
		assertEquals(1000, parts.values().stream().mapToLong(Long::longValue).sum());
		assertEquals(334, parts.get(a));
		assertEquals(333, parts.get(b));
		assertEquals(333, parts.get(c));
		assertEquals(Map.of(a, 1L), SplitMath.split(1, List.of(a)));
		assertEquals(2, SplitMath.split(1, List.of(a, b, c)).values().stream().filter(v -> v == 0).count());
	}

	@Test
	void centsRoundHalfUpAndKeepPrecision() {
		assertEquals(1999, SplitMath.cents(new BigDecimal("19.99")));
		assertEquals(1000, SplitMath.cents(new BigDecimal("10")));
		assertEquals(101, SplitMath.cents(new BigDecimal("1.005")));
		assertEquals(new BigDecimal("0.07"), SplitMath.euros(7));
	}

	private SharedExpense expense(UUID id, UUID payer, String amount) {
		return SharedExpense.builder().id(id).paidBy(payer).amount(new BigDecimal(amount)).expenseDate(LocalDate.now()).build();
	}

	private SharedShare share(UUID expense, UUID user, String amount) {
		return SharedShare.builder().expenseId(expense).userId(user).amount(new BigDecimal(amount)).build();
	}

	@Test
	void balancesSumToZeroAndSettlementsReduceTheDebt() {
		UUID e1 = UUID.randomUUID();
		UUID e2 = UUID.randomUUID();
		// a paga 90 per tutti e tre; b paga 30 solo per se' e per a
		var exps = List.of(expense(e1, a, "90"), expense(e2, b, "30"));
		var parts = List.of(share(e1, a, "30"), share(e1, b, "30"), share(e1, c, "30"), share(e2, a, "15"), share(e2, b, "15"));

		Map<UUID, Long> net = SplitMath.balances(exps, parts, List.of());
		assertEquals(45_00, net.get(a)); // pagato 90, dovuto 45
		assertEquals(-15_00, net.get(b)); // pagato 30, dovuto 45
		assertEquals(-30_00, net.get(c));
		assertEquals(0, net.values().stream().mapToLong(Long::longValue).sum());

		var paid = List.of(SharedSettlement.builder().fromUser(c).toUser(a).amount(new BigDecimal("30")).settleDate(LocalDate.now()).build());
		Map<UUID, Long> after = SplitMath.balances(exps, parts, paid);
		assertEquals(15_00, after.get(a));
		assertEquals(0, after.get(c));
	}

	@Test
	void orphanSharesOfDeletedExpensesAreIgnored() {
		Map<UUID, Long> net = SplitMath.balances(List.of(), List.of(share(UUID.randomUUID(), a, "10")), List.of());
		assertTrue(net.isEmpty());
	}

	@Test
	void simplifyUsesFewestPaymentsAndMatchesTheBalances() {
		// c deve 30 ad a; b deve 15 ad a
		List<Debt> debts = SplitMath.simplify(Map.of(a, 45_00L, b, -15_00L, c, -30_00L));
		assertEquals(List.of(new Debt(c, a, new BigDecimal("30.00")), new Debt(b, a, new BigDecimal("15.00"))), debts);
		// una catena a->b->c si accorcia
		List<Debt> chain = SplitMath.simplify(Map.of(a, -10_00L, b, 0L, c, 10_00L));
		assertEquals(List.of(new Debt(a, c, new BigDecimal("10.00"))), chain);
		assertTrue(SplitMath.simplify(Map.of(a, 0L, b, 0L)).isEmpty());
	}
}
