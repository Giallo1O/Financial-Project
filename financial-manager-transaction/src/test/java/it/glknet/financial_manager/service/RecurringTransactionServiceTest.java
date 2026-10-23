package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import it.glknet.financial_manager.dto.TransactionRequest;
import it.glknet.financial_manager.model.RecurringTransaction;
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.model.TransactionType;
import it.glknet.financial_manager.repository.RecurringTransactionRepository;

class RecurringTransactionServiceTest {

	private final RecurringTransactionRepository repo = mock(RecurringTransactionRepository.class);
	private final TransactionService transactions = mock(TransactionService.class);
	private final RecurringTransactionService service = new RecurringTransactionService(repo, transactions);

	private final UUID owner = UUID.randomUUID();

	private RecurringTransaction rent(LocalDate due) {
		return RecurringTransaction.builder().id(UUID.randomUUID()).userId(owner).description("Affitto")
				.amount(new BigDecimal("500")).type(TransactionType.USCITA).dayOfMonth(31).nextDueDate(due).build();
	}

	@Test
	void confirmCreatesTransactionAndMovesDueDateToNextMonthClampedToMonthEnd() {
		RecurringTransaction r = rent(LocalDate.of(2026, 1, 31));
		when(repo.findById(r.getId())).thenReturn(Optional.of(r));
		when(transactions.createTransaction(any())).thenReturn(Transaction.builder().build());

		service.confirm(owner, r.getId(), null);

		ArgumentCaptor<TransactionRequest> sent = ArgumentCaptor.forClass(TransactionRequest.class);
		verify(transactions).createTransaction(sent.capture());
		assertEquals(LocalDate.of(2026, 1, 31), sent.getValue().transactionDate());
		assertEquals(owner, sent.getValue().userId());
		assertEquals(LocalDate.of(2026, 2, 28), r.getNextDueDate());
		assertEquals(LocalDate.of(2026, 1, 31), r.getLastGeneratedDate());
	}

	@Test
	void otherUsersRecurringTransactionIsNotFound() {
		RecurringTransaction r = rent(LocalDate.of(2026, 1, 31));
		when(repo.findById(r.getId())).thenReturn(Optional.of(r));

		assertThrows(ResponseStatusException.class, () -> service.confirm(UUID.randomUUID(), r.getId(), null));
		assertThrows(ResponseStatusException.class, () -> service.delete(UUID.randomUUID(), r.getId()));
		verify(transactions, never()).createTransaction(any());
	}
}
