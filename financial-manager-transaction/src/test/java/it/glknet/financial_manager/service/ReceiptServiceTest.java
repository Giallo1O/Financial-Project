package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import it.glknet.financial_manager.dto.CategorySpend;
import it.glknet.financial_manager.dto.ProductRow;
import it.glknet.financial_manager.dto.ProductSummary;
import it.glknet.financial_manager.dto.ReceiptItemDto;
import it.glknet.financial_manager.dto.ReceiptRequest;
import it.glknet.financial_manager.repository.ReceiptItemRepository;
import it.glknet.financial_manager.repository.ReceiptRepository;
import it.glknet.financial_manager.repository.TransactionRepository;

class ReceiptServiceTest {

	private final ReceiptRepository receipts = mock(ReceiptRepository.class);
	private final ReceiptItemRepository items = mock(ReceiptItemRepository.class);
	private final TransactionService transactions = mock(TransactionService.class);
	private final ReceiptService service = new ReceiptService(receipts, items, mock(TransactionRepository.class),
			transactions);
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
						BigDecimal.ONE)));

		ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.save(user, req));

		assertEquals(409, e.getStatusCode().value());
		verify(transactions, never()).createTransaction(any());
		assertTrue(e.getReason().contains("gia'"));
	}
}
