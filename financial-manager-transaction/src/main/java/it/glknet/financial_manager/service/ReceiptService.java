package it.glknet.financial_manager.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import it.glknet.financial_manager.dto.CategorySpend;
import it.glknet.financial_manager.dto.PricePoint;
import it.glknet.financial_manager.dto.ProductRow;
import it.glknet.financial_manager.dto.ProductSummary;
import it.glknet.financial_manager.dto.ReceiptDetail;
import it.glknet.financial_manager.dto.ReceiptItemDto;
import it.glknet.financial_manager.dto.ReceiptRequest;
import it.glknet.financial_manager.dto.TransactionRequest;
import it.glknet.financial_manager.model.Receipt;
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.model.ReceiptItem;
import it.glknet.financial_manager.model.TransactionType;
import it.glknet.financial_manager.repository.CategoryRepository;
import it.glknet.financial_manager.repository.ReceiptItemRepository;
import it.glknet.financial_manager.repository.ReceiptRepository;
import it.glknet.financial_manager.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReceiptService {

	private static final LocalDate MIN = LocalDate.of(1970, 1, 1);
	private static final LocalDate MAX = LocalDate.of(2999, 12, 31);

	private final ReceiptRepository receipts;
	private final ReceiptItemRepository items;
	private final TransactionRepository transactionRepository;
	private final CategoryRepository categoryRepository;
	private final TransactionService transactionService;

	/** Salva lo scontrino come spesa (transazione di uscita) piu' le righe prodotto, in un'unica transazione DB. */
	@Transactional
	public ReceiptDetail save(UUID userId, ReceiptRequest req) {
		if (req.purchaseDate() == null || req.total() == null || req.total().signum() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Servono data e totale positivo");
		}
		if (req.items() == null || req.items().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lo scontrino non ha articoli");
		}
		String store = req.storeName() == null || req.storeName().isBlank() ? "Spesa" : req.storeName().trim();
		if (receipts.existsByUserIdAndPurchaseDateAndTotalAndStoreNameIgnoreCase(userId, req.purchaseDate(),
				req.total(), store)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Questo scontrino e' gia' stato salvato");
		}

		Transaction tx;
		boolean created = req.linkTransactionId() == null;
		if (created) {
			tx = transactionService.createTransaction(new TransactionRequest("Spesa " + store, req.total(),
					TransactionType.USCITA, req.categoryId(), userId, req.purchaseDate(), req.accountId()));
		} else {
			tx = linkable(userId, req.purchaseDate(), req.total(), req.linkTransactionId());
			// il movimento resta com'e', salvo la categoria se non ne ha una e quella scelta e' dell'utente
			if (tx.getCategoryId() == null && req.categoryId() != null && categoryRepository
					.findById(req.categoryId()).filter(c -> userId.equals(c.getUserId())).isPresent()) {
				tx.setCategoryId(req.categoryId());
				transactionRepository.save(tx);
			}
		}
		Receipt receipt = receipts.save(Receipt.builder().userId(userId).storeName(store)
				.purchaseDate(req.purchaseDate()).total(req.total()).transactionId(tx.getId())
				.transactionCreated(created).build());

		List<ReceiptItem> saved = new ArrayList<>();
		for (ReceiptItemDto dto : req.items()) {
			if (dto.name() == null || dto.name().isBlank()) {
				continue;
			}
			saved.add(items.save(ReceiptItem.builder().receiptId(receipt.getId()).rawName(dto.rawName())
					.name(dto.name().trim()).foodCategory(blankToOther(dto.foodCategory()))
					.quantity(dto.quantity() == null ? BigDecimal.ONE : dto.quantity())
					.unit(dto.unit() == null || dto.unit().isBlank() ? "pz" : dto.unit().trim().toLowerCase())
					.unitPrice(dto.unitPrice()).totalPrice(dto.totalPrice())
					.discount(dto.discount() != null && dto.discount().signum() > 0 ? dto.discount() : null).build()));
		}
		return new ReceiptDetail(receipt, saved);
	}

	/**
	 * Movimenti in uscita gia' presenti (es. importati dalla banca) che potrebbero essere questo scontrino: stesso
	 * importo, data entro {@value TransactionService#MATCH_DAYS} giorni, non gia' collegati a un altro scontrino.
	 * I piu' vicini nel tempo per primi.
	 */
	public List<Transaction> matchCandidates(UUID userId, LocalDate date, BigDecimal total) {
		List<Transaction> sameAmount = transactionRepository
				.findByUserIdAndTransactionDateBetween(userId, date.minusDays(TransactionService.MATCH_DAYS),
						date.plusDays(TransactionService.MATCH_DAYS))
				.stream().filter(t -> t.getType() == TransactionType.USCITA && t.getAmount() != null
						&& t.getAmount().compareTo(total) == 0)
				.toList();
		if (sameAmount.isEmpty()) {
			return sameAmount;
		}
		Set<UUID> taken = receipts.findByTransactionIdIn(sameAmount.stream().map(Transaction::getId).toList())
				.stream().map(Receipt::getTransactionId).collect(Collectors.toSet());
		return sameAmount.stream().filter(t -> !taken.contains(t.getId()))
				.sorted(Comparator.comparingLong(
						t -> Math.abs(java.time.temporal.ChronoUnit.DAYS.between(t.getTransactionDate(), date))))
				.toList();
	}

	private Transaction linkable(UUID userId, LocalDate date, BigDecimal total, UUID transactionId) {
		return matchCandidates(userId, date, total).stream().filter(t -> t.getId().equals(transactionId)).findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Il movimento scelto non corrisponde piu' a questo scontrino"));
	}

	public List<Receipt> list(UUID userId) {
		return receipts.findByUserIdOrderByPurchaseDateDescCreatedAtDesc(userId);
	}

	public ReceiptDetail get(UUID userId, UUID id) {
		Receipt r = findOwned(userId, id);
		return new ReceiptDetail(r, items.findByReceiptId(id));
	}

	/** Elimina lo scontrino, le sue righe e la spesa collegata. */
	@Transactional
	public void delete(UUID userId, UUID id) {
		Receipt r = findOwned(userId, id);
		items.deleteByReceiptId(id);
		receipts.delete(r);
		if (r.getTransactionId() != null && !Boolean.FALSE.equals(r.getTransactionCreated())) {
			transactionRepository.findById(r.getTransactionId()).filter(t -> userId.equals(t.getUserId()))
					.ifPresent(transactionRepository::delete);
		}
	}

	/** Riepilogo per prodotto (stesso nome e unita'): numero di acquisti e prezzi pagati per unita' (sconti compresi). */
	public List<ProductSummary> products(UUID userId) {
		Map<String, List<ProductRow>> byProduct = items.findRows(userId, MIN, MAX).stream()
				.filter(r -> r.paidUnitPrice() != null)
				.collect(Collectors.groupingBy(r -> productKey(r.name(), r.unit()), LinkedHashMap::new,
						Collectors.toList()));
		List<ProductSummary> result = new ArrayList<>();
		for (List<ProductRow> rows : byProduct.values()) {
			rows.sort(Comparator.comparing(ProductRow::purchaseDate));
			ProductRow last = rows.get(rows.size() - 1);
			ProductRow cheapest = rows.stream().min(Comparator.comparing(ProductRow::paidUnitPrice)).orElse(last);
			BigDecimal sum = rows.stream().map(ProductRow::paidUnitPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
			result.add(new ProductSummary(last.name(), last.unit(), last.foodCategory(), rows.size(),
					last.paidUnitPrice(), cheapest.paidUnitPrice(),
					sum.divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP),
					rows.stream().map(ProductRow::paidUnitPrice).max(Comparator.naturalOrder()).orElse(last.paidUnitPrice()),
					last.purchaseDate(), last.storeName(), cheapest.storeName()));
		}
		result.sort(Comparator.comparing(ProductSummary::name, String.CASE_INSENSITIVE_ORDER));
		return result;
	}

	public List<PricePoint> history(UUID userId, String name, String unit) {
		String key = productKey(name, unit);
		return items.findRows(userId, MIN, MAX).stream()
				.filter(r -> productKey(r.name(), r.unit()).equals(key) && r.paidUnitPrice() != null)
				.sorted(Comparator.comparing(ProductRow::purchaseDate))
				.map(r -> new PricePoint(r.purchaseDate(), r.storeName(), r.paidUnitPrice(), r.quantity())).toList();
	}

	/** Spesa per tipo di alimento in un periodo (gli sconti riducono il totale della loro categoria). */
	public List<CategorySpend> spending(UUID userId, LocalDate from, LocalDate to) {
		Map<String, BigDecimal> totals = new LinkedHashMap<>();
		for (ProductRow r : items.findRows(userId, from, to)) {
			if (r.totalPrice() != null) {
				totals.merge(blankToOther(r.foodCategory()), r.totalPrice(), BigDecimal::add);
			}
		}
		return totals.entrySet().stream().map(e -> new CategorySpend(e.getKey(), e.getValue()))
				.sorted(Comparator.comparing(CategorySpend::total).reversed()).toList();
	}

	private Receipt findOwned(UUID userId, UUID id) {
		return receipts.findById(id).filter(r -> userId.equals(r.getUserId()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scontrino non trovato"));
	}

	private static String productKey(String name, String unit) {
		return (name == null ? "" : name.trim().toLowerCase().replaceAll("\\s+", " ")) + "|"
				+ (unit == null ? "" : unit.trim().toLowerCase());
	}

	private static String blankToOther(String s) {
		return s == null || s.isBlank() ? "Altro" : s.trim();
	}
}
