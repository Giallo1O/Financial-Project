package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import it.glknet.financial_manager.model.RecurringTransaction;
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.model.TransactionType;

class RecurringSuggestionServiceTest {

	private final LocalDate today = LocalDate.of(2026, 10, 25);
	private final UUID subscriptions = UUID.randomUUID();
	private final List<Transaction> txs = new ArrayList<>();

	private void monthly(String description, int months, int day, String... amounts) {
		for (int i = 0; i < months; i++) {
			LocalDate d = today.minusMonths(months - 1 - i).withDayOfMonth(day);
			txs.add(Transaction.builder().description(description).type(TransactionType.USCITA)
					.amount(new BigDecimal(amounts[i % amounts.length])).transactionDate(d)
					.categoryId(description.startsWith("SPOTIFY") ? subscriptions : null).build());
		}
	}

	@Test
	void findsFixedAndVariableMonthlySpendingButNotShopsOrEndedOnes() {
		monthly("SPOTIFY*P46F75FB2D", 6, 18, "20.99");
		monthly("Telecom Italia S.p.A", 5, 3, "25.00", "34.10", "31.00", "42.20", "28.50");
		monthly("NETFLIX.COM", 4, 2, "19.99"); // ultimo pagamento a inizio ottobre: ancora attivo
		monthly("BENNET 12", 6, 5, "40"); // spesa al supermercato...
		monthly("BENNET 8", 6, 20, "35"); // ...due volte al mese: non e' una ricorrenza
		for (int i = 0; i < 4; i++) { // abbonamento disdetto a maggio
			txs.add(Transaction.builder().description("PATREON").type(TransactionType.USCITA).amount(new BigDecimal("6.10"))
					.transactionDate(LocalDate.of(2026, 2 + i, 28)).build());
		}

		var s = RecurringSuggestionService.suggest(txs, List.of(), today);

		assertEquals(List.of("spotify", "telecom", "netflix"), s.stream().map(x -> x.keyword()).toList());
		var spotify = s.get(0);
		assertFalse(spotify.variableAmount());
		assertEquals(0, new BigDecimal("20.99").compareTo(spotify.amount()));
		assertEquals(18, spotify.dayOfMonth());
		assertEquals("SPOTIFY", spotify.description()); // senza il codice della banca
		assertEquals(subscriptions, spotify.categoryId());
		var telecom = s.get(1);
		assertTrue(telecom.variableAmount());
		assertEquals(0, new BigDecimal("33.90").compareTo(telecom.amount())); // media degli ultimi tre
	}

	@Test
	void merchantsWithAnActiveRecurringAreNotSuggestedAgain() {
		monthly("SPOTIFY*P46F75FB2D", 6, 18, "20.99");
		RecurringTransaction existing = RecurringTransaction.builder().description("Spotify")
				.type(TransactionType.USCITA).amount(new BigDecimal("20.99")).build();

		assertEquals(0, RecurringSuggestionService.suggest(txs, List.of(existing), today).size());
	}
}
