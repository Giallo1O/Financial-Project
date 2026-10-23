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
import it.glknet.financial_manager.repository.CategoryRepository;
import it.glknet.financial_manager.repository.ReceiptRepository;
import it.glknet.financial_manager.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionService {

	private final TransactionRepository repository;

	private final CategoryRepository categoryRepository;

	private final ReceiptRepository receiptRepository;

	@Transactional
	public Transaction createTransaction(TransactionRequest request) {
		if (request.amount() == null || request.amount().signum() < 0 || request.type() == null
				|| request.transactionDate() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Servono importo (non negativo), tipo e data");
		}
		// Controllo se esiste la categoria (se fornita)
		if (request.categoryId() != null) {
			Category category = categoryRepository.findById(request.categoryId()).orElse(null);
			if (category == null || !category.getUserId().equals(request.userId())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoria inserita non esiste");
			}
		}

		Transaction transaction = Transaction.builder().description(request.description()).amount(request.amount())
				.type(request.type()).categoryId(request.categoryId()).userId(request.userId())
				.transactionDate(request.transactionDate()).build();

		return repository.save(transaction);
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
		LocalDate min = rows.stream().map(TransactionRequest::transactionDate).filter(Objects::nonNull)
				.min(Comparator.naturalOrder()).orElse(null);
		LocalDate max = rows.stream().map(TransactionRequest::transactionDate).filter(Objects::nonNull)
				.max(Comparator.naturalOrder()).orElse(null);
		Map<String, Integer> existing = new HashMap<>();
		List<Receipt> coveringReceipts = new ArrayList<>();
		if (min != null) {
			for (Transaction t : repository.findByUserIdAndTransactionDateBetween(userId, min, max)) {
				existing.merge(key(t.getTransactionDate(), t.getAmount(), t.getType(), t.getDescription()), 1,
						Integer::sum);
			}
			for (Receipt r : receiptRepository.findByUserIdAndPurchaseDateBetween(userId, min.minusDays(MATCH_DAYS),
					max.plusDays(MATCH_DAYS))) {
				if (!Boolean.FALSE.equals(r.getTransactionCreated())) {
					coveringReceipts.add(r);
				}
			}
		}
		List<String> result = new ArrayList<>();
		for (TransactionRequest r : rows) {
			String k = key(r.transactionDate(), r.amount(), r.type(), r.description());
			int left = existing.getOrDefault(k, 0);
			existing.put(k, Math.max(0, left - 1));
			if (left > 0) {
				result.add("Già presente");
				continue;
			}
			Receipt covering = r.type() == TransactionType.USCITA ? takeCoveringReceipt(coveringReceipts, r) : null;
			result.add(covering == null ? null : "Già registrato con lo scontrino " + covering.getStoreName() + " del "
					+ covering.getPurchaseDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
		}
		return result;
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
		List<String> duplicates = findDuplicates(userId, rows);
		int imported = 0;
		for (int i = 0; i < rows.size(); i++) {
			if (duplicates.get(i) == null) {
				createTransaction(rows.get(i));
				imported++;
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
