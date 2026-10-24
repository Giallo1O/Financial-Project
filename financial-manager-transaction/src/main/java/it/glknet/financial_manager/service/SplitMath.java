package it.glknet.financial_manager.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import it.glknet.financial_manager.dto.GroupViews.Debt;
import it.glknet.financial_manager.model.SharedExpense;
import it.glknet.financial_manager.model.SharedSettlement;
import it.glknet.financial_manager.model.SharedShare;

/** Conti in centesimi (niente decimali: la somma delle quote fa sempre esattamente la spesa). */
final class SplitMath {

	private SplitMath() {
	}

	static long cents(BigDecimal amount) {
		return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
	}

	static BigDecimal euros(long cents) {
		return BigDecimal.valueOf(cents, 2);
	}

	/** Divide in parti uguali; i centesimi che avanzano vanno uno ciascuno ai primi della lista. */
	static Map<UUID, Long> split(long cents, List<UUID> participants) {
		Map<UUID, Long> out = new LinkedHashMap<>();
		int n = participants.size();
		long base = cents / n;
		long extra = cents % n;
		for (int i = 0; i < n; i++) {
			out.put(participants.get(i), base + (i < extra ? 1 : 0));
		}
		return out;
	}

	/** Saldo di ognuno: positivo = gli altri gli devono, negativo = deve lui. La somma e' sempre zero. */
	static Map<UUID, Long> balances(List<SharedExpense> expenses, List<SharedShare> shares,
			List<SharedSettlement> settlements) {
		Map<UUID, Long> net = new HashMap<>();
		Map<UUID, UUID> payer = new HashMap<>();
		for (SharedExpense e : expenses) {
			payer.put(e.getId(), e.getPaidBy());
			net.merge(e.getPaidBy(), cents(e.getAmount()), Long::sum);
		}
		for (SharedShare s : shares) {
			if (payer.containsKey(s.getExpenseId())) {
				net.merge(s.getUserId(), -cents(s.getAmount()), Long::sum);
			}
		}
		for (SharedSettlement s : settlements) {
			net.merge(s.getFromUser(), cents(s.getAmount()), Long::sum);
			net.merge(s.getToUser(), -cents(s.getAmount()), Long::sum);
		}
		return net;
	}

	/** Pochi passaggi per pareggiare tutto: chi deve di piu' paga chi deve ricevere di piu'. */
	static List<Debt> simplify(Map<UUID, Long> net) {
		List<Map.Entry<UUID, Long>> credit = new ArrayList<>();
		List<Map.Entry<UUID, Long>> debit = new ArrayList<>();
		Comparator<Map.Entry<UUID, Long>> byAmount = Comparator.<Map.Entry<UUID, Long>, Long>comparing(Map.Entry::getValue)
				.reversed().thenComparing(e -> e.getKey().toString());
		net.forEach((user, v) -> {
			if (v > 0) {
				credit.add(Map.entry(user, v));
			} else if (v < 0) {
				debit.add(Map.entry(user, -v));
			}
		});
		credit.sort(byAmount);
		debit.sort(byAmount);
		List<Debt> debts = new ArrayList<>();
		long[] c = credit.stream().mapToLong(Map.Entry::getValue).toArray();
		long[] d = debit.stream().mapToLong(Map.Entry::getValue).toArray();
		int i = 0;
		int j = 0;
		while (i < c.length && j < d.length) {
			long pay = Math.min(c[i], d[j]);
			debts.add(new Debt(debit.get(j).getKey(), credit.get(i).getKey(), euros(pay)));
			c[i] -= pay;
			d[j] -= pay;
			if (c[i] == 0) {
				i++;
			}
			if (d[j] == 0) {
				j++;
			}
		}
		return debts;
	}
}
