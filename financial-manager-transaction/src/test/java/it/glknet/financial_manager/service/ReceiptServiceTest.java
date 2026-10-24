package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import it.glknet.financial_manager.dto.CategorySpend;
import it.glknet.financial_manager.dto.ProductRow;
import it.glknet.financial_manager.dto.ProductSummary;
import it.glknet.financial_manager.dto.ReceiptItemDto;
import it.glknet.financial_manager.dto.ReceiptRequest;
import it.glknet.financial_manager.model.Category;
import it.glknet.financial_manager.model.Receipt;
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.model.TransactionType;
import it.glknet.financial_manager.repository.CategoryRepository;
import it.glknet.financial_manager.repository.ReceiptItemRepository;
import it.glknet.financial_manager.repository.ReceiptRepository;
import it.glknet.financial_manager.repository.TransactionRepository;

class ReceiptServiceTest {

	private final ReceiptRepository receipts = mock(ReceiptRepository.class);
	private final ReceiptItemRepository items = mock(ReceiptItemRepository.class);
	private final TransactionService transactions = mock(TransactionService.class);
	private final TransactionRepository txRepo = mock(TransactionRepository.class);
	private final CategoryRepository categories = mock(CategoryRepository.class);
	private final ReceiptService service = new ReceiptService(receipts, items, txRepo, categories, transactions);
	private final UUID user = UUID.randomUUID();

	private static ProductRow row(String name, String unit, String cat, String unitPrice, String total, String date,
			String store) {
		return new ProductRow(name, unit, cat, BigDecimal.ONE, new BigDecimal(unitPrice), new BigDecimal(total),
				LocalDate.parse(date), store);
	}

	@Test
	void productStatsUseLatestMinAndCheapestStore() {
		when(items.findRows(any(), any(), any())).thenReturn(List.of(
				row("Latte 1L", "pz", "Latticini e uova", "1.40", "1.40", "2026-09-01", "Coop"),
				row("latte  1l", "pz", "Latticini e uova", "1.20", "1.20", "2026-10-01", "Lidl"),
				row("Latte 1L", "pz", "Latticini e uova", "1.60", "1.60", "2026-10-15", "Esselunga"),
				row("Sconto: Latte 1L", "pz", "Latticini e uova", "-0.20", "-0.20", "2026-10-15", "Esselunga")));

		ProductSummary p = service.products(user).get(0);

		assertEquals(3, p.purchases());
		assertEquals(0, new BigDecimal("1.60").compareTo(p.lastUnitPrice()));
		assertEquals(0, new BigDecimal("1.20").compareTo(p.minUnitPrice()));
		assertEquals(0, new BigDecimal("1.40").compareTo(p.avgUnitPrice()));
		assertEquals("Lidl", p.cheapestStore());
		assertEquals("Esselunga", p.lastStore());
		assertEquals(1, service.products(user).size()); // lo sconto non e' un prodotto
	}

	@Test
	void productStatsUseThePricePaidAfterTheDiscount() {
		// 15 birre a 1,28 di listino con 6,45 di sconto: pagate 12,75, cioe' 0,85 l'una
		when(items.findRows(any(), any(), any())).thenReturn(List.of(
				new ProductRow("Birra", "pz", "Bevande", new BigDecimal("15"), new BigDecimal("1.28"),
						new BigDecimal("12.75"), LocalDate.parse("2026-10-24"), "Bennet"),
				new ProductRow("Birra", "pz", "Bevande", new BigDecimal("6"), new BigDecimal("0.99"),
						new BigDecimal("5.94"), LocalDate.parse("2026-10-20"), "Lidl")));

		ProductSummary p = service.products(user).get(0);

		assertEquals(0, new BigDecimal("0.85").compareTo(p.lastUnitPrice()));
		assertEquals(0, new BigDecimal("0.85").compareTo(p.minUnitPrice()));
		assertEquals("Bennet", p.cheapestStore()); // con lo sconto conviene Bennet, non Lidl
		assertEquals(0, new BigDecimal("0.85").compareTo(service.history(user, "Birra", "pz").get(1).unitPrice()));
	}

	@Test
	void spendingSubtractsDiscountsFromTheirCategory() {
		when(items.findRows(any(), any(), any())).thenReturn(List.of(
				row("Spaghetti", "pz", "Dispensa", "1.20", "4.80", "2026-10-18", "Esselunga"),
				row("Sconto: Spaghetti", "pz", "Dispensa", "-0.20", "-0.20", "2026-10-18", "Esselunga"),
				row("Banane", "kg", "Frutta e verdura", "1.99", "1.06", "2026-10-18", "Esselunga")));

		List<CategorySpend> spend = service.spending(user, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

		assertEquals("Dispensa", spend.get(0).foodCategory());
		assertEquals(0, new BigDecimal("4.60").compareTo(spend.get(0).total()));
		assertEquals(2, spend.size());
	}

	@Test
	void savingTheSameReceiptTwiceIsRejectedWithoutCreatingATransaction() {
		when(receipts.existsByUserIdAndPurchaseDateAndTotalAndStoreNameIgnoreCase(any(), any(), any(), anyString()))
				.thenReturn(true);
		ReceiptRequest req = new ReceiptRequest("Esselunga", LocalDate.of(2026, 10, 18), new BigDecimal("9.76"), null,
				List.of(new ReceiptItemDto("X", "Yogurt", "Latticini e uova", BigDecimal.ONE, "pz", BigDecimal.ONE,
						BigDecimal.ONE, null)),
				null);

		ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.save(user, req));

		assertEquals(409, e.getStatusCode().value());
		verify(transactions, never()).createTransaction(any());
		assertTrue(e.getReason().contains("gia'"));
	}

	// ---- collegamento con un movimento gia' importato dalla banca

	private Transaction bankTx(String date, String amount, TransactionType type, UUID categoryId) {
		return Transaction.builder().id(UUID.randomUUID()).userId(user).description("PAGAMENTO POS")
				.amount(new BigDecimal(amount)).type(type).categoryId(categoryId)
				.transactionDate(LocalDate.parse(date)).build();
	}

	private ReceiptRequest request(UUID categoryId, UUID link) {
		return new ReceiptRequest("Esselunga", LocalDate.of(2026, 10, 18), new BigDecimal("9.76"), categoryId,
				List.of(new ReceiptItemDto("X", "Yogurt", "Latticini e uova", BigDecimal.ONE, "pz", BigDecimal.ONE,
						BigDecimal.ONE, null)),
				link);
	}

	@Test
	void matchCandidatesAreSameAmountExpensesNearInTimeNotAlreadyLinked() {
		Transaction near = bankTx("2026-10-19", "9.76", TransactionType.USCITA, null);
		Transaction nearer = bankTx("2026-10-18", "9.76", TransactionType.USCITA, null);
		Transaction taken = bankTx("2026-10-18", "9.76", TransactionType.USCITA, null);
		when(txRepo.findByUserIdAndTransactionDateBetween(any(), any(), any())).thenReturn(List.of(near, nearer, taken,
				bankTx("2026-10-18", "9.77", TransactionType.USCITA, null),
				bankTx("2026-10-18", "9.76", TransactionType.ENTRATA, null)));
		when(receipts.findByTransactionIdIn(any()))
				.thenReturn(List.of(Receipt.builder().transactionId(taken.getId()).build()));

		List<Transaction> found = service.matchCandidates(user, LocalDate.of(2026, 10, 18), new BigDecimal("9.76"));

		assertEquals(List.of(nearer, near), found); // il piu' vicino per primo; esclusi importo diverso, entrata, gia' collegato
	}

	@Test
	void linkingDoesNotCreateANewExpenseAndFillsMissingCategory() {
		Transaction bank = bankTx("2026-10-19", "9.76", TransactionType.USCITA, null);
		UUID cat = UUID.randomUUID();
		when(txRepo.findByUserIdAndTransactionDateBetween(any(), any(), any())).thenReturn(List.of(bank));
		when(receipts.findByTransactionIdIn(any())).thenReturn(List.of());
		when(categories.findById(cat)).thenReturn(Optional.of(Category.builder().id(cat).userId(user).build()));
		when(receipts.save(any())).thenAnswer(i -> i.getArgument(0));
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		var detail = service.save(user, request(cat, bank.getId()));

		verify(transactions, never()).createTransaction(any());
		assertEquals(bank.getId(), detail.receipt().getTransactionId());
		assertEquals(Boolean.FALSE, detail.receipt().getTransactionCreated());
		assertEquals(cat, bank.getCategoryId());
	}

	@Test
	void linkingToAMovementThatIsNotACandidateIsRejected() {
		when(txRepo.findByUserIdAndTransactionDateBetween(any(), any(), any())).thenReturn(List.of());

		ResponseStatusException e = assertThrows(ResponseStatusException.class,
				() -> service.save(user, request(null, UUID.randomUUID())));

		assertEquals(400, e.getStatusCode().value());
		verify(transactions, never()).createTransaction(any());
	}

	@Test
	void deletingALinkedReceiptKeepsTheBankMovement() {
		UUID txId = UUID.randomUUID();
		Receipt linked = Receipt.builder().id(UUID.randomUUID()).userId(user).transactionId(txId)
				.transactionCreated(false).build();
		Receipt own = Receipt.builder().id(UUID.randomUUID()).userId(user).transactionId(txId)
				.transactionCreated(true).build();
		when(receipts.findById(linked.getId())).thenReturn(Optional.of(linked));
		when(receipts.findById(own.getId())).thenReturn(Optional.of(own));
		when(txRepo.findById(txId)).thenReturn(Optional.of(bankTx("2026-10-19", "9.76", TransactionType.USCITA, null)));

		service.delete(user, linked.getId());
		verify(txRepo, never()).delete(any());

		service.delete(user, own.getId()); // la spesa creata dallo scontrino sparisce con lui
		verify(txRepo, times(1)).delete(any());
	}
}
