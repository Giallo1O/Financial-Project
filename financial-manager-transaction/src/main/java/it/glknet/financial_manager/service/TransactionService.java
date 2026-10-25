package it.glknet.financial_manager.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import it.glknet.financial_manager.dto.ImportResult;
import it.glknet.financial_manager.dto.TransactionRequest;
import it.glknet.financial_manager.model.Category;
import it.glknet.financial_manager.model.Receipt;
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.model.TransactionType;
import it.glknet.financial_manager.repository.AccountRepository;
import it.glknet.financial_manager.repository.CategoryRepository;
import it.glknet.financial_manager.repository.ReceiptRepository;
import it.glknet.financial_manager.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionService {

	private final TransactionRepository repository;

	private final CategoryRepository categoryRepository;

	private final AccountRepository accountRepository;

	private final ReceiptRepository receiptRepository;

	/** Importo massimo di un singolo movimento: oltre e' quasi certamente un errore di battitura o di import. */
	public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000");

	@Transactional
	public Transaction createTransaction(TransactionRequest request) {
		validate(request);
		Transaction transaction = Transaction.builder().description(request.description()).amount(request.amount())
				.type(request.type()).categoryId(request.categoryId()).accountId(request.accountId()).userId(request.userId())
				.transactionDate(request.transactionDate()).build();

		return repository.save(transaction);
	}

	@Transactional
	public Transaction updateTransaction(UUID id, TransactionRequest request) {
		Transaction tx = repository.findById(id).filter(t -> request.userId().equals(t.getUserId()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transazione non trovata"));
		validate(request);
		tx.setDescription(request.description());
		tx.setAmount(request.amount());
		tx.setType(request.type());
		tx.setCategoryId(request.categoryId());
		tx.setAccountId(request.accountId());
		tx.setTransactionDate(request.transactionDate());
		return repository.save(tx);
	}

	private void validate(TransactionRequest request) {
		if (request.amount() == null || request.amount().signum() <= 0 || request.type() == null
				|| request.transactionDate() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Servono importo (maggiore di zero), tipo e data");
		}
		if (request.amount().compareTo(MAX_AMOUNT) > 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Importo troppo alto (massimo 1.000.000 €)");
		}
		// Controllo se esiste la categoria (se fornita)
		if (request.categoryId() != null) {
			Category category = categoryRepository.findById(request.categoryId()).orElse(null);
			if (category == null || !category.getUserId().equals(request.userId())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoria inserita non esiste");
			}
		}
		if (request.accountId() != null && !accountRepository.findById(request.accountId())
				.filter(a -> a.getUserId().equals(request.userId())).isPresent()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il conto scelto non esiste");
		}
	}

	public List<Transaction> getAllTransactions(UUID userId) {
		return repository.findByUserId(userId);
	}

	public List<Transaction> getTransactionsByMonth(UUID userId, int month, int year) {
		return repository.findByUserIdAndMonthAndYear(userId, month, year);
	}

	/** Giorni di scarto tra la data dello scontrino e quella del movimento in banca (valuta/registrazione in ritardo). */
	public static final int MATCH_DAYS = 3;

	/**
	 * Per ogni riga restituisce null se e' nuova, altrimenti il motivo per cui e' un doppione:
	 * - esiste gia' un movimento uguale (stessa data, importo, tipo e descrizione). Si contano le occorrenze: se nel DB
	 *   c'e' 1 caffe' identico e nel file 2, solo il primo e' un doppione;
	 * - e' un'uscita gia' registrata da uno scontrino (stesso importo, data entro {@value #MATCH_DAYS} giorni), che vince
	 *   perche' ha il dettaglio dei prodotti. Ogni scontrino copre al massimo una riga.
	 */
	public List<String> findDuplicates(UUID userId, List<TransactionRequest> rows) {
		return analyze(userId, rows).stream().map(Match::note).toList();
	}

	/** Esito del controllo di una riga: null se nuova; recurring e' il movimento di una ricorrenza da aggiornare. */
	private record Match(String note, Transaction recurring) {
	}

	/** Giorni di scarto tra la scadenza di una ricorrenza e l'addebito vero in banca. */
	public static final int RECURRING_DAYS = 5;

	private List<Match> analyze(UUID userId, List<TransactionRequest> rows) {
		LocalDate min = rows.stream().map(TransactionRequest::transactionDate).filter(Objects::nonNull)
				.min(Comparator.naturalOrder()).orElse(null);
		LocalDate max = rows.stream().map(TransactionRequest::transactionDate).filter(Objects::nonNull)
				.max(Comparator.naturalOrder()).orElse(null);
		Map<String, Integer> existing = new HashMap<>();
		// Stessa data, importo e tipo ma descrizione diversa (es. "Esselunga" scritto a mano, "Esselunga Milano" in banca)
		Map<String, List<String>> sameAmount = new HashMap<>();
		// Movimenti creati da una ricorrenza: la banca li addebita qualche giorno dopo, e per le bollette con un altro importo
		List<Transaction> fromRecurring = new ArrayList<>();
		List<Receipt> coveringReceipts = new ArrayList<>();
		if (min != null) {
			for (Transaction t : repository.findByUserIdAndTransactionDateBetween(userId, min.minusDays(RECURRING_DAYS),
					max.plusDays(RECURRING_DAYS))) {
				if (t.getRecurringId() != null) {
					fromRecurring.add(t);
				}
				if (t.getTransactionDate().isBefore(min) || t.getTransactionDate().isAfter(max)) {
					continue;
				}
				existing.merge(key(t.getTransactionDate(), t.getAmount(), t.getType(), t.getDescription()), 1,
						Integer::sum);
				if (t.getRecurringId() == null) {
					sameAmount.computeIfAbsent(key(t.getTransactionDate(), t.getAmount(), t.getType(), ""),
							k -> new ArrayList<>()).add(t.getDescription() == null ? "" : t.getDescription());
				}
			}
			for (Receipt r : receiptRepository.findByUserIdAndPurchaseDateBetween(userId, min.minusDays(MATCH_DAYS),
					max.plusDays(MATCH_DAYS))) {
				if (!Boolean.FALSE.equals(r.getTransactionCreated())) {
					coveringReceipts.add(r);
				}
			}
		}
		List<Match> result = new ArrayList<>();
		for (TransactionRequest r : rows) {
			String k = key(r.transactionDate(), r.amount(), r.type(), r.description());
			List<String> similar = sameAmount.getOrDefault(key(r.transactionDate(), r.amount(), r.type(), ""),
					new ArrayList<>());
			int left = existing.getOrDefault(k, 0);
			existing.put(k, Math.max(0, left - 1));
			if (left > 0) {
				String same = key(null, null, null, r.description());
				similar.stream().filter(d -> key(null, null, null, d).equals(same)).findFirst().ifPresent(similar::remove);
				result.add(new Match("Già presente", null));
				continue;
			}
			Transaction recurring = closestSameSpending(fromRecurring, r.type(), r.description(), r.amount(),
					r.transactionDate());
			if (recurring != null) {
				fromRecurring.remove(recurring);
				result.add(new Match("Già registrato dalla ricorrenza \"" + recurring.getDescription() + "\" del "
						+ recurring.getTransactionDate().format(DAY) + ": prende data e importo della banca", recurring));
				continue;
			}
			if (!similar.isEmpty()) {
				String other = similar.remove(0);
				existing.computeIfPresent(key(r.transactionDate(), r.amount(), r.type(), other), (x, n) -> n - 1);
				result.add(new Match("Stessa data e importo di \"" + other + "\"", null));
				continue;
			}
			Receipt covering = r.type() == TransactionType.USCITA ? takeCoveringReceipt(coveringReceipts, r) : null;
			result.add(new Match(covering == null ? null : "Già registrato con lo scontrino " + covering.getStoreName()
					+ " del " + covering.getPurchaseDate().format(DAY), null));
		}
		return result;
	}

	private static final java.time.format.DateTimeFormatter DAY = java.time.format.DateTimeFormatter
			.ofPattern("dd/MM/yyyy");

	/**
	 * Il movimento piu' vicino nel tempo (entro {@value #RECURRING_DAYS} giorni) che e' la stessa spesa: stesso tipo e
	 * stesso esercente (parola chiave) oppure stesso importo. L'esercente basta per le bollette, che cambiano importo.
	 */
	private static Transaction closestSameSpending(List<Transaction> candidates, TransactionType type,
			String description, BigDecimal amount, LocalDate date) {
		if (date == null) {
			return null;
		}
		String keyword = TextKeywords.extractKeyword(description);
		Transaction best = null;
		long bestGap = Long.MAX_VALUE;
		for (Transaction t : candidates) {
			long gap = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(t.getTransactionDate(), date));
			boolean sameMerchant = keyword != null && keyword.equals(TextKeywords.extractKeyword(t.getDescription()));
			boolean sameAmount = amount != null && t.getAmount() != null && amount.compareTo(t.getAmount()) == 0;
			if (t.getType() == type && gap <= RECURRING_DAYS && (sameMerchant || sameAmount) && gap < bestGap) {
				best = t;
				bestGap = gap;
			}
		}
		return best;
	}

	/**
	 * Movimento di una ricorrenza (conferma manuale o automatica). Se la stessa spesa e' gia' arrivata con l'estratto
	 * conto (entro {@value #RECURRING_DAYS} giorni dalla scadenza), collega quel movimento invece di crearne un secondo.
	 */
	@Transactional
	public Transaction createForRecurring(TransactionRequest request, UUID recurringId) {
		LocalDate date = request.transactionDate();
		if (date != null) {
			List<Transaction> bank = repository.findByUserIdAndTransactionDateBetween(request.userId(),
					date.minusDays(RECURRING_DAYS), date.plusDays(RECURRING_DAYS)).stream()
					.filter(t -> t.getRecurringId() == null).toList();
			Transaction already = closestSameSpending(bank, request.type(), request.description(), request.amount(), date);
			if (already != null) {
				already.setRecurringId(recurringId);
				if (already.getCategoryId() == null) {
					already.setCategoryId(request.categoryId());
				}
				return repository.save(already);
			}
		}
		validate(request);
		return repository.save(Transaction.builder().description(request.description()).amount(request.amount())
				.type(request.type()).categoryId(request.categoryId()).userId(request.userId())
				.transactionDate(date).recurringId(recurringId).build());
	}

	private static Receipt takeCoveringReceipt(List<Receipt> receipts, TransactionRequest row) {
		if (row.transactionDate() == null || row.amount() == null) {
			return null;
		}
		Receipt best = null;
		for (Receipt r : receipts) {
			long gap = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(r.getPurchaseDate(), row.transactionDate()));
			if (gap <= MATCH_DAYS && r.getTotal().compareTo(row.amount()) == 0 && (best == null || gap < Math
					.abs(java.time.temporal.ChronoUnit.DAYS.between(best.getPurchaseDate(), row.transactionDate())))) {
				best = r;
			}
		}
		if (best != null) {
			receipts.remove(best);
		}
		return best;
	}

	/** Importa le righe non ancora presenti; le altre vengono saltate. */
	@Transactional
	public ImportResult importAll(UUID userId, List<TransactionRequest> rows) {
		List<Match> matches = analyze(userId, rows);
		int imported = 0;
		for (int i = 0; i < rows.size(); i++) {
			Match m = matches.get(i);
			TransactionRequest row = rows.get(i);
			if (m.note() == null) {
				createTransaction(row);
				imported++;
			} else if (m.recurring() != null) {
				// La spesa della ricorrenza diventa quella vera della banca (data, importo, descrizione), resta collegata
				validate(row);
				Transaction t = m.recurring();
				t.setAmount(row.amount());
				t.setTransactionDate(row.transactionDate());
				t.setDescription(row.description());
				if (t.getCategoryId() == null) {
					t.setCategoryId(row.categoryId());
				}
				repository.save(t);
			}
		}
		return new ImportResult(imported, rows.size() - imported);
	}

	private static String key(LocalDate date, BigDecimal amount, TransactionType type, String description) {
		String desc = description == null ? "" : description.trim().toLowerCase().replaceAll("\\s+", " ");
		return date + "|" + (amount == null ? "" : amount.stripTrailingZeros().toPlainString()) + "|" + type + "|"
				+ desc;
	}

	@Transactional
	public void deleteTransaction(UUID id, UUID userId) {
		Transaction tx = repository.findById(id).filter(t -> userId.equals(t.getUserId()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transazione non trovata"));

		repository.delete(tx);
	}
}
