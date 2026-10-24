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
				new TransactionRequest("x", BigDecimal.ZERO, ok.type(), null, user, day),
				new TransactionRequest("x", new BigDecimal("99999999999999"), ok.type(), null, user, day),
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

	@Test
	void sameDateAndAmountWithDifferentDescriptionIsFlaggedOnce() {
		existing(1); // "caffe" a mano; in banca "BAR CENTRALE" stesso giorno e importo, due volte
		var notes = service.findDuplicates(user, List.of(coffee("BAR CENTRALE"), coffee("BAR CENTRALE")));
		assertEquals(java.util.Arrays.asList("Stessa data e importo di \"caffe\"", null), notes);
	}

	// ---- ricorrenze e import: la stessa spesa non va contata due volte

	private final UUID recurringId = UUID.randomUUID();

	@Test
	void bankRowOfAVariableBillUpdatesTheMovementCreatedByItsRecurring() {
		// la ricorrenza ha registrato la bolletta il 3 con l'importo stimato; la banca la addebita il 5 con quello vero
		Transaction fromRecurring = Transaction.builder().id(UUID.randomUUID()).userId(user).description("Telecom")
				.amount(new BigDecimal("33.90")).type(TransactionType.USCITA).transactionDate(LocalDate.of(2026, 10, 3))
				.recurringId(recurringId).build();
		when(repo.findByUserIdAndTransactionDateBetween(any(), any(), any())).thenReturn(List.of(fromRecurring));
		when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
		var bank = new TransactionRequest("Telecom Italia S.p.A", new BigDecimal("41.20"), TransactionType.USCITA, null,
				user, LocalDate.of(2026, 10, 5));

		assertEquals("Già registrato dalla ricorrenza \"Telecom\" del 03/10/2026: prende data e importo della banca",
				service.findDuplicates(user, List.of(bank)).get(0));
		assertEquals(new ImportResult(0, 1), service.importAll(user, List.of(bank)));
		assertEquals(0, new BigDecimal("41.20").compareTo(fromRecurring.getAmount()));
		assertEquals(LocalDate.of(2026, 10, 5), fromRecurring.getTransactionDate());
		assertEquals(recurringId, fromRecurring.getRecurringId());
	}

	@Test
	void confirmingARecurringLinksTheBankMovementAlreadyImported() {
		Transaction bank = Transaction.builder().id(UUID.randomUUID()).userId(user).description("SPOTIFY*P46F75FB2D")
				.amount(new BigDecimal("20.99")).type(TransactionType.USCITA).transactionDate(LocalDate.of(2026, 10, 19))
				.build();
		Transaction other = Transaction.builder().id(UUID.randomUUID()).userId(user).description("BENNET 12")
				.amount(new BigDecimal("31.40")).type(TransactionType.USCITA).transactionDate(LocalDate.of(2026, 10, 18))
				.build();
		when(repo.findByUserIdAndTransactionDateBetween(any(), any(), any())).thenReturn(List.of(other, bank));
		when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

		Transaction result = service.createForRecurring(new TransactionRequest("Spotify", new BigDecimal("20.99"),
				TransactionType.USCITA, null, user, LocalDate.of(2026, 10, 18)), recurringId);

		assertEquals(bank.getId(), result.getId()); // nessun movimento nuovo
		assertEquals(recurringId, bank.getRecurringId());
		verify(repo, times(1)).save(any());
	}

	@Test
	void confirmingARecurringWithoutBankMovementCreatesIt() {
		when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

		Transaction result = service.createForRecurring(new TransactionRequest("Spotify", new BigDecimal("20.99"),
				TransactionType.USCITA, null, user, LocalDate.of(2026, 10, 18)), recurringId);

		assertEquals(recurringId, result.getRecurringId());
		assertEquals(0, new BigDecimal("20.99").compareTo(result.getAmount()));
	}
}
