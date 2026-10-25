package it.glknet.financial_manager.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import it.glknet.financial_manager.model.RecurringTransaction;
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.model.TransactionType;
import it.glknet.financial_manager.repository.RecurringTransactionRepository;
import it.glknet.financial_manager.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;

/**
 * Trova nei movimenti degli ultimi 12 mesi le spese (o entrate) che si ripetono ogni mese: stesso esercente, al
 * massimo circa una volta al mese, in almeno {@value #MIN_MONTHS} mesi quasi consecutivi e ancora attive. L'importo
 * e' "fisso" se quasi sempre uguale, altrimenti "variabile" (bollette) e si propone la media degli ultimi tre.
 */
@Service
@RequiredArgsConstructor
public class RecurringSuggestionService {

	public record Suggestion(String keyword, String description, TransactionType type, BigDecimal amount,
			boolean variableAmount, int dayOfMonth, UUID categoryId, int months, LocalDate lastDate) {
	}

	static final int MIN_MONTHS = 3;
	/** Oltre questi giorni dall'ultimo pagamento la ricorrenza si considera finita (abbonamento disdetto). */
	static final int ACTIVE_DAYS = 45;

	private final TransactionRepository transactions;
	private final RecurringTransactionRepository recurring;

	public List<Suggestion> suggest(UUID userId) {
		LocalDate today = LocalDate.now();
		return suggest(transactions.findByUserIdAndTransactionDateBetween(userId, today.minusMonths(12), today),
				recurring.findByUserIdOrderByDayOfMonthAsc(userId), today);
	}

	static List<Suggestion> suggest(List<Transaction> txs, List<RecurringTransaction> existing, LocalDate today) {
		// esercenti che hanno gia' una ricorrenza attiva dello stesso tipo
		Set<String> covered = existing.stream().filter(RecurringTransaction::isActive)
				.map(r -> TextKeywords.extractKeyword(r.getDescription()) + "|" + r.getType())
				.collect(Collectors.toSet());
		Map<String, List<Transaction>> groups = new LinkedHashMap<>();
		for (Transaction t : txs) {
			String k = TextKeywords.extractKeyword(t.getDescription());
			if (k != null && t.getAmount() != null && t.getTransactionDate() != null) {
				groups.computeIfAbsent(k + "|" + t.getType(), x -> new ArrayList<>()).add(t);
			}
		}
		List<Suggestion> result = new ArrayList<>();
		for (Map.Entry<String, List<Transaction>> e : groups.entrySet()) {
			List<Transaction> list = e.getValue();
			list.sort(Comparator.comparing(Transaction::getTransactionDate));
			Set<YearMonth> months = list.stream().map(t -> YearMonth.from(t.getTransactionDate()))
					.collect(Collectors.toSet());
			Transaction last = list.get(list.size() - 1);
			long span = ChronoUnit.MONTHS.between(YearMonth.from(list.get(0).getTransactionDate()),
					YearMonth.from(last.getTransactionDate())) + 1;
			if (months.size() < MIN_MONTHS // troppo pochi mesi
					|| list.size() > months.size() * 1.25 // piu' volte al mese: un negozio, non un abbonamento
					|| months.size() < span * 0.7 // mesi sparsi
					|| last.getTransactionDate().isBefore(today.minusDays(ACTIVE_DAYS)) // non piu' attiva
					|| covered.contains(e.getKey())) {
				continue;
			}
			List<BigDecimal> amounts = list.stream().map(Transaction::getAmount).sorted().toList();
			BigDecimal median = amounts.get(amounts.size() / 2);
			BigDecimal tolerance = median.multiply(new BigDecimal("0.10")).max(BigDecimal.ONE);
			long stable = amounts.stream().filter(a -> a.subtract(median).abs().compareTo(tolerance) <= 0).count();
			boolean variable = stable < amounts.size() * 0.8;
			BigDecimal amount = variable
					? list.subList(Math.max(0, list.size() - 3), list.size()).stream().map(Transaction::getAmount)
							.reduce(BigDecimal.ZERO, BigDecimal::add)
							.divide(BigDecimal.valueOf(Math.min(3, list.size())), 2, RoundingMode.HALF_UP)
					: median;
			List<Integer> days = list.stream().map(t -> t.getTransactionDate().getDayOfMonth()).sorted().toList();
			UUID category = list.stream().map(Transaction::getCategoryId).filter(Objects::nonNull)
					.collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
					.max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
			result.add(new Suggestion(e.getKey().substring(0, e.getKey().lastIndexOf('|')), readableName(last.getDescription()),
					last.getType(), amount, variable, days.get(days.size() / 2), category, months.size(),
					last.getTransactionDate()));
		}
		result.sort(Comparator.comparingInt(Suggestion::months).reversed().thenComparing(Suggestion::keyword));
		return result;
	}

	/** Toglie i codici della banca dal nome: "SPOTIFY*P481C85CC7" diventa "SPOTIFY", "NOW C1039 Pass Ent" "NOW Pass Ent". */
	static String readableName(String description) {
		String clean = String.join(" ", java.util.Arrays.stream(description.split("[\\s*]+"))
				.filter(w -> !w.isBlank() && !w.matches(".*\\d.*")).toList());
		return clean.isBlank() ? description : clean;
	}
}
