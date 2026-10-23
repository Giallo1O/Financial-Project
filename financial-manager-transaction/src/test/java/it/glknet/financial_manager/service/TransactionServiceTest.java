package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import it.glknet.financial_manager.dto.ImportResult;
import it.glknet.financial_manager.dto.TransactionRequest;
import it.glknet.financial_manager.model.Receipt;
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.model.TransactionType;
import it.glknet.financial_manager.repository.CategoryRepository;
import it.glknet.financial_manager.repository.ReceiptRepository;
import it.glknet.financial_manager.repository.TransactionRepository;

class TransactionServiceTest {

	private final TransactionRepository repo = mock(TransactionRepository.class);
	private final ReceiptRepository receiptRepo = mock(ReceiptRepository.class);
	private final TransactionService service = new TransactionService(repo, mock(CategoryRepository.class),
			receiptRepo);
	private final UUID user = UUID.randomUUID();
	private final LocalDate day = LocalDate.of(2026, 3, 11);

	private TransactionRequest coffee(String desc) {
		return new TransactionRequest(desc, new BigDecimal("1.20"), TransactionType.USCITA, null, user, day);
	}

	private void existing(int copies) {
		List<Transaction> rows = java.util.stream.IntStream.range(0, copies)
				.mapToObj(i -> Transaction.builder().description("caffe").amount(new BigDecimal("1.2"))
						.type(TransactionType.USCITA).userId(user).transactionDate(day).build())
				.toList();
		when(repo.findByUserIdAndTransactionDateBetween(any(), any(), any())).thenReturn(rows);
	}

	@Test
	void incompleteOrNegativeTransactionsAreRejectedWith400() {
		var ok = coffee("x");
		for (TransactionRequest bad : List.of(
				new TransactionRequest("x", null, ok.type(), null, user, day),
				new TransactionRequest("x", new BigDecimal("-1"), ok.type(), null, user, day),
				new TransactionRequest("x", ok.amount(), null, null, user, day),
				new TransactionRequest("x", ok.amount(), ok.type(), null, user, null))) {
			assertThrows(org.springframework.web.server.ResponseStatusException.class,
					() -> service.createTransaction(bad));
		}
		verify(repo, times(0)).save(any());
	}

	@Test
	void duplicatesAreCountedNotJustMatched() {
		existing(1); // nel DB c'e' 1 caffe'; nel file 2: solo il primo e' un doppione
		var notes = service.findDuplicates(user, List.of(coffee("Caffe"), coffee(" caffe  ")));
		assertEquals(java.util.Arrays.asList("Già presente", null), notes);
	}

	@Test
	void importSkipsRowsAlreadyPresent() {
		existing(2);
		when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
		ImportResult r = service.importAll(user, List.of(coffee("caffe"), coffee("caffe"), coffee("caffe")));
		assertEquals(new ImportResult(1, 2), r);
		verify(repo, times(1)).save(any());
	}

	private Receipt receipt(String store, String date, String total, Boolean created) {
		return Receipt.builder().storeName(store).purchaseDate(LocalDate.parse(date)).total(new BigDecimal(total))
				.transactionCreated(created).build();
	}

	private TransactionRequest bankRow(String date, String amount, TransactionType type) {
		return new TransactionRequest("PAGAMENTO POS 1234", new BigDecimal(amount), type, null, user,
				LocalDate.parse(date));
	}

	@Test
	void bankRowCoveredByReceiptWithinThreeDaysButOnlyOncePerReceipt() {
		when(receiptRepo.findByUserIdAndPurchaseDateBetween(any(), any(), any()))
				.thenReturn(List.of(receipt("Esselunga", "2026-10-18", "9.76", true)));

		var notes = service.findDuplicates(user, List.of(bankRow("2026-10-20", "9.76", TransactionType.USCITA),
				bankRow("2026-10-20", "9.76", TransactionType.USCITA)));

		assertEquals("Già registrato con lo scontrino Esselunga del 18/10/2026", notes.get(0));
		assertEquals(null, notes.get(1)); // un solo scontrino: la seconda riga e' un altro acquisto
	}

	@Test
	void bankRowNotCoveredWhenTooFarDifferentAmountIncomeOrReceiptLinkedToBankMovement() {
		when(receiptRepo.findByUserIdAndPurchaseDateBetween(any(), any(), any())).thenReturn(List.of(
				receipt("Esselunga", "2026-10-18", "9.76", true), receipt("Coop", "2026-10-19", "5.00", false)));

		var notes = service.findDuplicates(user,
				List.of(bankRow("2026-10-22", "9.76", TransactionType.USCITA), // 4 giorni: troppo lontano
						bankRow("2026-10-18", "9.77", TransactionType.USCITA), // importo diverso
						bankRow("2026-10-18", "9.76", TransactionType.ENTRATA), // un'entrata non e' una spesa
						bankRow("2026-10-19", "5.00", TransactionType.USCITA))); // scontrino gia' collegato alla banca

		assertEquals(java.util.Arrays.asList(null, null, null, null), notes);
	}
}
