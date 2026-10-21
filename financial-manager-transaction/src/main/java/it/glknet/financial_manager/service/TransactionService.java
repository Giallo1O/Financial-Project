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
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.model.TransactionType;
import it.glknet.financial_manager.repository.CategoryRepository;
import it.glknet.financial_manager.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionService {

	private final TransactionRepository repository;

	private final CategoryRepository categoryRepository;

	@Transactional
	public Transaction createTransaction(TransactionRequest request) {
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

	/**
	 * Per ogni riga dice se esiste gia' un movimento uguale (stessa data, importo, tipo e descrizione).
	 * Si contano le occorrenze: se nel DB c'e' 1 caffe' identico e nel file 2, solo il primo e' doppione.
	 */
	public List<Boolean> markDuplicates(UUID userId, List<TransactionRequest> rows) {
		LocalDate min = rows.stream().map(TransactionRequest::transactionDate).filter(Objects::nonNull)
				.min(Comparator.naturalOrder()).orElse(null);
		LocalDate max = rows.stream().map(TransactionRequest::transactionDate).filter(Objects::nonNull)
				.max(Comparator.naturalOrder()).orElse(null);
		Map<String, Integer> existing = new HashMap<>();
		if (min != null) {
			for (Transaction t : repository.findByUserIdAndTransactionDateBetween(userId, min, max)) {
				existing.merge(key(t.getTransactionDate(), t.getAmount(), t.getType(), t.getDescription()), 1,
						Integer::sum);
			}
		}
		List<Boolean> result = new ArrayList<>();
		for (TransactionRequest r : rows) {
			String k = key(r.transactionDate(), r.amount(), r.type(), r.description());
			int left = existing.getOrDefault(k, 0);
			existing.put(k, Math.max(0, left - 1));
			result.add(left > 0);
		}
		return result;
	}

	/** Importa le righe non ancora presenti; le altre vengono saltate. */
	@Transactional
	public ImportResult importAll(UUID userId, List<TransactionRequest> rows) {
		List<Boolean> duplicates = markDuplicates(userId, rows);
		int imported = 0;
		for (int i = 0; i < rows.size(); i++) {
			if (!duplicates.get(i)) {
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
