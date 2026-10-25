package it.glknet.financial_manager.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import it.glknet.financial_manager.dto.TransactionRequest;
import it.glknet.financial_manager.model.Category;
import it.glknet.financial_manager.model.CategoryRule;
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.model.TransactionType;
import it.glknet.financial_manager.repository.CategoryRepository;
import it.glknet.financial_manager.repository.CategoryRuleRepository;
import it.glknet.financial_manager.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;

/** Regole "parola chiave -> categoria": suggeriscono la categoria nell'import e imparano dalle scelte dell'utente. */
@Service
@RequiredArgsConstructor
public class CategoryRuleService {

	/** La categoria suggerita e la parola chiave della regola che l'ha prodotta. */
	public record Suggestion(UUID categoryId, String keyword) {
	}

	/**
	 * Movimenti senza categoria dello stesso esercente (stessa parola chiave) e dello stesso tipo: si categorizzano
	 * tutti insieme creando la regola per la parola chiave e applicandola.
	 */
	public record MerchantGroup(String keyword, TransactionType type, int count, BigDecimal total,
			List<String> examples) {
	}

	private final CategoryRuleRepository rules;
	private final CategoryRepository categories;
	private final TransactionRepository transactions;

	/** Applica le regole ai movimenti gia' salvati che non hanno categoria; restituisce quanti ne ha categorizzati. */
	@Transactional
	public int applyToUncategorized(UUID userId) {
		Matcher matcher = new Matcher(rules.findByUserId(userId), categoriesOf(userId));
		int updated = 0;
		for (Transaction t : transactions.findByUserId(userId)) {
			Suggestion s = t.getCategoryId() != null ? null : matcher.best(t.getDescription(), t.getType());
			if (s != null) {
				t.setCategoryId(s.categoryId());
				transactions.save(t);
				updated++;
			}
		}
		return updated;
	}

	/** Movimenti senza categoria raggruppati per esercente, i gruppi piu' numerosi per primi. */
	public List<MerchantGroup> uncategorized(UUID userId) {
		Map<String, List<Transaction>> groups = new LinkedHashMap<>();
		for (Transaction t : transactions.findByUserId(userId)) {
			if (t.getCategoryId() == null) {
				groups.computeIfAbsent(merchantKeyword(t.getDescription()) + "|" + t.getType(), k -> new ArrayList<>())
						.add(t);
			}
		}
		return groups.values().stream().map(list -> new MerchantGroup(merchantKeyword(list.get(0).getDescription()),
				list.get(0).getType(), list.size(),
				list.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
				list.stream().map(Transaction::getDescription).distinct().limit(3).toList()))
				.sorted(Comparator.comparingInt(MerchantGroup::count).reversed()
						.thenComparing(MerchantGroup::total, Comparator.reverseOrder()))
				.toList();
	}

	/** La parola chiave dell'esercente; se la descrizione non ne ha una (es. "D+") la descrizione normalizzata. */
	private static String merchantKeyword(String description) {
		String k = TextKeywords.extractKeyword(description);
		return k != null ? k : TextKeywords.normalize(description);
	}

	public List<CategoryRule> list(UUID userId) {
		return rules.findByUserId(userId);
	}

	/**
	 * Aggiunge o aggiorna la regola per quella parola chiave e quel tipo: la stessa parola puo' avere una regola per le
	 * uscite e una per le entrate (es. una persona a cui mandi e da cui ricevi soldi).
	 */
	@Transactional
	public CategoryRule save(UUID userId, String keyword, UUID categoryId) {
		String normalized = TextKeywords.normalize(keyword);
		if (normalized.length() < 3) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La parola chiave deve avere almeno 3 caratteri");
		}
		Category chosen = categories.findById(categoryId).filter(c -> userId.equals(c.getUserId()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria non valida"));
		Map<UUID, Category> cats = categoriesOf(userId);
		CategoryRule rule = rules.findByUserId(userId).stream()
				.filter(r -> r.getKeyword().equals(normalized) && cats.containsKey(r.getCategoryId())
						&& cats.get(r.getCategoryId()).isExpense() == chosen.isExpense())
				.findFirst().orElseGet(() -> CategoryRule.builder().userId(userId).keyword(normalized).build());
		rule.setCategoryId(categoryId);
		return rules.save(rule);
	}

	@Transactional
	public void delete(UUID userId, UUID id) {
		CategoryRule rule = rules.findById(id).filter(r -> userId.equals(r.getUserId()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Regola non trovata"));
		rules.delete(rule);
	}

	/**
	 * Per ogni riga che non ha ancora una categoria, la regola piu' specifica che vale (piu' parole, poi piu'
	 * lunga, poi la piu' recente) la cui categoria e' dello stesso tipo della riga (uscita/entrata). Null se nessuna.
	 */
	public List<Suggestion> suggest(UUID userId, List<TransactionRequest> rows) {
		Matcher matcher = new Matcher(rules.findByUserId(userId), categoriesOf(userId));
		List<Suggestion> result = new ArrayList<>();
		for (TransactionRequest r : rows) {
			result.add(r.categoryId() != null ? null : matcher.best(r.description(), r.type()));
		}
		return result;
	}

	/**
	 * Impara dalle categorie scelte dall'utente nelle righe importate: se una regola esistente ha dato un'altra
	 * categoria la corregge, altrimenti crea una regola con la parola chiave della descrizione.
	 */
	@Transactional
	public int learn(UUID userId, List<TransactionRequest> rows) {
		Map<UUID, Category> cats = categoriesOf(userId);
		List<CategoryRule> current = new ArrayList<>(rules.findByUserId(userId));
		int changed = 0;
		for (TransactionRequest r : rows) {
			Category chosen = r.categoryId() == null ? null : cats.get(r.categoryId());
			if (chosen == null || chosen.isExpense() != (r.type() == TransactionType.USCITA)) {
				continue;
			}
			Suggestion matched = new Matcher(current, cats).best(r.description(), r.type());
			if (matched != null) {
				if (!matched.categoryId().equals(chosen.getId())) {
					CategoryRule rule = current.stream().filter(x -> x.getKeyword().equals(matched.keyword()))
							.findFirst().orElseThrow();
					rule.setCategoryId(chosen.getId());
					rules.save(rule);
					changed++;
				}
				continue;
			}
			String keyword = TextKeywords.extractKeyword(r.description());
			if (keyword != null) {
				current.add(rules.save(CategoryRule.builder().userId(userId).keyword(keyword)
						.categoryId(chosen.getId()).build()));
				changed++;
			}
		}
		return changed;
	}

	/**
	 * Crea regole dalle transazioni gia' categorizzate: una per ogni parola chiave che compare almeno 2 volte e
	 * che ha quasi sempre (80%) la stessa categoria. Non tocca le regole esistenti.
	 */
	@Transactional
	public int learnFromHistory(UUID userId) {
		Map<UUID, Category> cats = categoriesOf(userId);
		Map<String, Map<UUID, Integer>> counts = new HashMap<>();
		for (Transaction t : transactions.findByUserId(userId)) {
			Category c = t.getCategoryId() == null ? null : cats.get(t.getCategoryId());
			String keyword = TextKeywords.extractKeyword(t.getDescription());
			if (c == null || keyword == null || c.isExpense() != (t.getType() == TransactionType.USCITA)) {
				continue;
			}
			counts.computeIfAbsent(keyword + "|" + c.isExpense(), k -> new HashMap<>()).merge(c.getId(), 1, Integer::sum);
		}
		// regole esistenti per parola chiave e tipo (uscita/entrata)
		List<String> existing = rules.findByUserId(userId).stream().filter(r -> cats.containsKey(r.getCategoryId()))
				.map(r -> r.getKeyword() + "|" + cats.get(r.getCategoryId()).isExpense()).toList();
		int created = 0;
		for (Map.Entry<String, Map<UUID, Integer>> e : counts.entrySet()) {
			int total = e.getValue().values().stream().mapToInt(Integer::intValue).sum();
			Map.Entry<UUID, Integer> top = e.getValue().entrySet().stream().max(Map.Entry.comparingByValue())
					.orElseThrow();
			if (total >= 2 && top.getValue() >= 0.8 * total && !existing.contains(e.getKey())) {
				String keyword = e.getKey().substring(0, e.getKey().lastIndexOf('|'));
				rules.save(CategoryRule.builder().userId(userId).keyword(keyword).categoryId(top.getKey()).build());
				created++;
			}
		}
		return created;
	}

	private Map<UUID, Category> categoriesOf(UUID userId) {
		return categories.findByUserId(userId).stream().collect(Collectors.toMap(Category::getId, Function.identity()));
	}

	/** Cerca la regola migliore tra quelle date. */
	private static final class Matcher {
		private record Prepared(CategoryRule rule, List<String> tokens, Category category) {
		}

		private final List<Prepared> prepared;

		Matcher(List<CategoryRule> rules, Map<UUID, Category> cats) {
			prepared = rules.stream().filter(r -> cats.containsKey(r.getCategoryId()))
					.map(r -> new Prepared(r, TextKeywords.tokens(r.getKeyword()), cats.get(r.getCategoryId())))
					.sorted(Comparator.<Prepared>comparingInt(p -> p.tokens().size())
							.thenComparingInt(p -> p.rule().getKeyword().length())
							.thenComparing(p -> p.rule().getCreatedAt(), Comparator.nullsFirst(Comparator.naturalOrder()))
							.reversed())
					.toList();
		}

		Suggestion best(String description, TransactionType type) {
			List<String> tokens = TextKeywords.tokens(description);
			for (Prepared p : prepared) {
				if (p.category().isExpense() == (type == TransactionType.USCITA)
						&& TextKeywords.matches(tokens, p.tokens())) {
					return new Suggestion(p.category().getId(), p.rule().getKeyword());
				}
			}
			return null;
		}
	}
}
