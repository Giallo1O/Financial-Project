package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.model.TransactionType;
import it.glknet.financial_manager.repository.CategoryRepository;
import it.glknet.financial_manager.repository.TransactionRepository;

class TransactionServiceTest {

	private final TransactionRepository repo = mock(TransactionRepository.class);
	private final TransactionService service = new TransactionService(repo, mock(CategoryRepository.class));
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
	void duplicatesAreCountedNotJustMatched() {
		existing(1); // nel DB c'e' 1 caffe'; nel file 2: solo il primo e' un doppione
		var flags = service.markDuplicates(user, List.of(coffee("Caffe"), coffee(" caffe  ")));
		assertEquals(List.of(true, false), flags);
	}

	@Test
	void importSkipsRowsAlreadyPresent() {
		existing(2);
		when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
		ImportResult r = service.importAll(user, List.of(coffee("caffe"), coffee("caffe"), coffee("caffe")));
		assertEquals(new ImportResult(1, 2), r);
		verify(repo, times(1)).save(any());
	}
}
