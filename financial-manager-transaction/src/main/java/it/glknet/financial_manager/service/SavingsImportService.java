package it.glknet.financial_manager.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.glknet.financial_manager.dto.TransactionRequest;
import it.glknet.financial_manager.model.ImportSource;
import it.glknet.financial_manager.model.SavingsGoal;
import it.glknet.financial_manager.model.SavingsMovement;
import it.glknet.financial_manager.model.TransactionType;
import it.glknet.financial_manager.repository.ImportSourceRepository;
import it.glknet.financial_manager.repository.SavingsGoalRepository;
import it.glknet.financial_manager.repository.SavingsMovementRepository;
import lombok.RequiredArgsConstructor;

/**
 * Salvadanai alimentati dagli estratti conto. Un salvadanaio collegato a un IBAN (es. il conto risparmi in un'altra
 * banca) riceve i bonifici verso quell'IBAN come versamenti e quelli in arrivo da lì come prelievi: non sono ne' spese
 * ne' entrate. Uno collegato a uno Space N26 segue il saldo dello Space: spostamenti dal/al conto principale e
 * spese pagate direttamente dallo Space (che restano anche spese vere).
 */
@Service
@RequiredArgsConstructor
public class SavingsImportService {

	public static final String SPACE = "SPACE";
	public static final String IBAN = "IBAN";

	/** Versamento (importo positivo) o prelievo (negativo) di un salvadanaio. */
	public record SavingsMove(UUID goalId, String goalName, LocalDate date, BigDecimal amount, String description) {
	}

	/** Per una riga dell'estratto conto: il movimento del salvadanaio e se la riga resta anche un movimento normale. */
	public record RowSavings(SavingsMove move, boolean alsoTransaction) {
	}

	public record ApplyResult(int applied, int skipped) {
	}

	private final SavingsGoalRepository goals;
	private final SavingsMovementRepository movements;
	private final ImportSourceRepository sources;

	/** Per ogni riga (stesso ordine): il versamento/prelievo del salvadanaio collegato, o null. */
	public List<RowSavings> forRows(UUID userId, List<TransactionRequest> rows, List<CsvImportService.Source> srcs) {
		Links links = links(userId);
		List<RowSavings> result = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			TransactionRequest r = rows.get(i);
			CsvImportService.Source s = srcs.get(i);
			boolean out = r.type() == TransactionType.USCITA;
			SavingsGoal byIban = s.iban().isEmpty() ? null : links.iban.get(s.iban());
			SavingsGoal bySpace = s.account().isEmpty() ? null : links.space.get(s.account().toLowerCase(Locale.ROOT));
			if (byIban != null) { // bonifico verso il conto risparmi: entra nel salvadanaio
				result.add(new RowSavings(new SavingsMove(byIban.getId(), byIban.getName(), r.transactionDate(),
						out ? r.amount() : r.amount().negate(), (out ? "Bonifico verso " : "Bonifico da ") + s.partner()),
						false));
			} else if (bySpace != null) { // pagato direttamente dallo Space: e' una spesa e lo Space scende
				result.add(new RowSavings(new SavingsMove(bySpace.getId(), bySpace.getName(), r.transactionDate(),
						out ? r.amount().negate() : r.amount(), r.description()), true));
			} else {
				result.add(null);
			}
		}
		return result;
	}

	/** Spostamenti tra conto principale e uno Space collegato a un salvadanaio (si usa il lato dello Space). */
	public List<SavingsMove> forInternal(UUID userId, List<CsvImportService.Internal> internal) {
		Links links = links(userId);
		List<SavingsMove> result = new ArrayList<>();
		for (CsvImportService.Internal t : internal) {
			SavingsGoal g = links.space.get(t.account().toLowerCase(Locale.ROOT));
			if (g != null) {
				result.add(new SavingsMove(g.getId(), g.getName(), t.date(), t.amount(),
						(t.amount().signum() > 0 ? "Spostamento da " : "Spostamento verso ") + t.partner()));
			}
		}
		return result;
	}

	/**
	 * Applica versamenti e prelievi ai salvadanai, saltando quelli gia' registrati (stesso salvadanaio, data, importo e
	 * descrizione: reimportare lo stesso estratto conto non li conta due volte).
	 */
	@Transactional
	public ApplyResult apply(UUID userId, List<SavingsMove> moves) {
		Map<String, Integer> existing = new HashMap<>();
		for (SavingsMovement m : movements.findByUserId(userId)) {
			existing.merge(key(m.getGoalId(), m.getDate(), m.getAmount(), m.getDescription()), 1, Integer::sum);
		}
		Map<UUID, SavingsGoal> owned = new HashMap<>();
		goals.findByUserIdOrderByCreatedAtDesc(userId).forEach(g -> owned.put(g.getId(), g));
		int applied = 0;
		for (SavingsMove m : moves) {
			SavingsGoal g = m.goalId() == null ? null : owned.get(m.goalId());
			String k = key(m.goalId(), m.date(), m.amount(), m.description());
			int left = existing.getOrDefault(k, 0);
			if (g == null || m.amount() == null || m.date() == null || left > 0) {
				existing.put(k, Math.max(0, left - 1));
				continue;
			}
			movements.save(SavingsMovement.builder().goalId(g.getId()).userId(userId).date(m.date())
					.amount(m.amount()).description(m.description()).build());
			g.setCurrentAmount(g.getCurrentAmount().add(m.amount()).max(BigDecimal.ZERO));
			if (g.getTargetAmount() != null) {
				g.setCompleted(g.getCurrentAmount().compareTo(g.getTargetAmount()) >= 0);
			}
			goals.save(g);
			applied++;
		}
		return new ApplyResult(applied, moves.size() - applied);
	}

	/** Ricorda Spaces e IBAN visti nell'estratto conto: sono le scelte per collegare un salvadanaio. */
	@Transactional
	public void rememberSources(UUID userId, CsvImportService.Parsed parsed) {
		Map<String, String> seen = new LinkedHashMap<>(); // tipo|chiave -> etichetta
		for (CsvImportService.Source s : parsed.sources()) {
			if (!s.account().isEmpty()) {
				seen.put(SPACE + "|" + s.account(), s.account());
			}
			if (!s.iban().isEmpty()) {
				seen.put(IBAN + "|" + s.iban(), s.partner());
			}
		}
		for (CsvImportService.Internal t : parsed.internal()) {
			if (!t.account().isEmpty()) {
				seen.put(SPACE + "|" + t.account(), t.account());
			}
		}
		Map<String, ImportSource> current = new HashMap<>();
		sources.findByUserId(userId).forEach(s -> current.put(s.getType() + "|" + s.getKey(), s));
		for (Map.Entry<String, String> e : seen.entrySet()) {
			int bar = e.getKey().indexOf('|');
			ImportSource s = current.getOrDefault(e.getKey(), ImportSource.builder().userId(userId)
					.type(e.getKey().substring(0, bar)).key(e.getKey().substring(bar + 1)).build());
			s.setLabel(e.getValue());
			s.setLastSeen(LocalDate.now());
			sources.save(s);
		}
	}

	/** Spaces e IBAN visti, prima gli Spaces, poi gli IBAN per nome. */
	public List<ImportSource> sources(UUID userId) {
		return sources.findByUserId(userId).stream()
				.sorted(Comparator.comparing(ImportSource::getType).reversed()
						.thenComparing(s -> s.getLabel() == null ? "" : s.getLabel(), String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	private record Links(Map<String, SavingsGoal> iban, Map<String, SavingsGoal> space) {
	}

	private Links links(UUID userId) {
		Map<String, SavingsGoal> iban = new HashMap<>();
		Map<String, SavingsGoal> space = new HashMap<>();
		for (SavingsGoal g : goals.findByUserIdOrderByCreatedAtDesc(userId)) {
			if (g.getSourceKey() == null || g.getSourceKey().isBlank()) {
				continue;
			}
			if (IBAN.equals(g.getSourceType())) {
				iban.putIfAbsent(CsvImportService.normalizeIban(g.getSourceKey()), g);
			} else if (SPACE.equals(g.getSourceType())) {
				space.putIfAbsent(g.getSourceKey().toLowerCase(Locale.ROOT), g);
			}
		}
		return new Links(iban, space);
	}

	private static String key(UUID goal, LocalDate date, BigDecimal amount, String description) {
		return goal + "|" + date + "|" + (amount == null ? "" : amount.stripTrailingZeros().toPlainString()) + "|"
				+ (description == null ? "" : description.trim().toLowerCase(Locale.ROOT));
	}
}
