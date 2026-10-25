package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import it.glknet.financial_manager.dto.AccountsOverview;
import it.glknet.financial_manager.model.Account;
import it.glknet.financial_manager.model.Transfer;
import it.glknet.financial_manager.model.TransactionType;
import it.glknet.financial_manager.repository.AccountRepository;
import it.glknet.financial_manager.repository.RecurringTransactionRepository;
import it.glknet.financial_manager.repository.TransactionRepository;
import it.glknet.financial_manager.repository.TransferRepository;

class AccountServiceTest {

	private final AccountRepository accounts = mock(AccountRepository.class);
	private final TransferRepository transfers = mock(TransferRepository.class);
	private final TransactionRepository transactions = mock(TransactionRepository.class);
	private final RecurringTransactionRepository recurring = mock(RecurringTransactionRepository.class);
	private final AccountService service = new AccountService(accounts, transfers, transactions, recurring);
	private final UUID user = UUID.randomUUID();
	private final LocalDate today = LocalDate.of(2026, 5, 10);

	private Account account(String name, String initial) {
		Account a = Account.builder().id(UUID.randomUUID()).userId(user).name(name).type("CORRENTE")
				.initialBalance(new BigDecimal(initial)).build();
		when(accounts.findById(a.getId())).thenReturn(Optional.of(a));
		return a;
	}

	private Transfer transfer(Account from, Account to, String amount, LocalDate date) {
		return Transfer.builder().userId(user).fromAccountId(from.getId()).toAccountId(to.getId())
				.amount(new BigDecimal(amount)).transferDate(date).build();
	}

	@Test
	void balanceIsInitialPlusIncomeMinusExpensesPlusTransfers() {
		Account bank = account("Banca", "1000");
		Account cash = account("Contanti", "50");
		when(accounts.findByUserIdOrderByCreatedAtAsc(user)).thenReturn(List.of(bank, cash));
		List<Object[]> sums = new ArrayList<>();
		sums.add(new Object[] { bank.getId(), TransactionType.ENTRATA, new BigDecimal("200") });
		sums.add(new Object[] { bank.getId(), TransactionType.USCITA, new BigDecimal("80.50") });
		sums.add(new Object[] { null, TransactionType.USCITA, new BigDecimal("10") });
		when(transactions.sumsByAccount(any(), any())).thenReturn(sums);
		when(transfers.findByUserIdOrderByTransferDateDescCreatedAtDesc(user))
				.thenReturn(List.of(transfer(bank, cash, "100", today.minusDays(1)),
						transfer(cash, bank, "999", today.plusDays(3))));

		AccountsOverview o = service.overview(user, today);

		assertEquals(0, new BigDecimal("1019.50").compareTo(o.accounts().get(0).balance()));
		assertEquals(0, new BigDecimal("150").compareTo(o.accounts().get(1).balance()));
		assertEquals(0, new BigDecimal("-10").compareTo(o.unassigned()));
		assertTrue(o.hasUnassigned());
		assertEquals(0, new BigDecimal("1159.50").compareTo(o.total()));
	}

	@Test
	void transferBetweenOwnAccountsMovesMoneyOnlyOnce() {
		Account a = account("A", "0");
		Account b = account("B", "0");
		when(transfers.save(any())).thenAnswer(i -> i.getArgument(0));

		Transfer t = service.createTransfer(user, a.getId(), b.getId(), new BigDecimal("25"), null, "  affitto  ");

		assertEquals("affitto", t.getDescription());
		assertEquals(LocalDate.now(), t.getTransferDate());
	}

	@Test
	void invalidTransfersAreRejected() {
		Account a = account("A", "0");
		Account b = account("B", "0");
		UUID stranger = UUID.randomUUID();
		Account foreign = Account.builder().id(stranger).userId(UUID.randomUUID()).name("X").build();
		when(accounts.findById(stranger)).thenReturn(Optional.of(foreign));

		assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.createTransfer(user, a.getId(), a.getId(), BigDecimal.TEN, null, null)));
		assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.createTransfer(user, a.getId(), b.getId(), BigDecimal.ZERO, null, null)));
		assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.createTransfer(user, a.getId(), b.getId(), null, null, null)));
		assertEquals(HttpStatus.NOT_FOUND, status(() -> service.createTransfer(user, a.getId(), stranger, BigDecimal.TEN, null, null)));
		verify(transfers, never()).save(any());
	}

	@Test
	void accountWithTransfersCannotBeDeletedButOthersDetachTheirTransactions() {
		Account used = account("Usato", "0");
		Account free = account("Libero", "0");
		when(transfers.existsByFromAccountIdOrToAccountId(used.getId(), used.getId())).thenReturn(true);

		assertEquals(HttpStatus.CONFLICT, status(() -> service.delete(user, used.getId())));
		verify(accounts, never()).delete(used);

		service.delete(user, free.getId());
		verify(transactions).detachAccount(free.getId());
		verify(recurring).detachAccount(free.getId());
		verify(accounts).delete(free);
	}

	@Test
	void otherPeoplesAccountsLookMissing() {
		UUID id = UUID.randomUUID();
		when(accounts.findById(id)).thenReturn(Optional.of(Account.builder().id(id).userId(UUID.randomUUID()).name("X").build()));

		assertEquals(HttpStatus.NOT_FOUND, status(() -> service.delete(user, id)));
		assertEquals(HttpStatus.NOT_FOUND, status(() -> service.assignUnassigned(user, id)));
		assertEquals(HttpStatus.NOT_FOUND, status(() -> service.update(user, id, "n", null, null, null, null)));
	}

	@Test
	void createValidatesAndNormalizes() {
		when(accounts.save(any())).thenAnswer(i -> i.getArgument(0));

		Account a = service.create(user, "  Revolut ", "carta", null, null);
		assertEquals("Revolut", a.getName());
		assertEquals("CARTA", a.getType());
		assertEquals(0, BigDecimal.ZERO.compareTo(a.getInitialBalance()));
		assertFalse(a.getColor().isBlank());
		assertEquals("ALTRO", service.create(user, "x", "boh", null, "#fff").getType());

		assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.create(user, "  ", null, null, null)));
		assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.create(user, "x".repeat(41), null, null, null)));
	}

	@Test
	void assignUnassignedReportsHowManyWereMoved() {
		Account a = account("A", "0");
		when(transactions.assignUnassigned(user, a.getId())).thenReturn(7);

		assertEquals(7, service.assignUnassigned(user, a.getId()));
	}

	private static HttpStatus status(Runnable r) {
		return (HttpStatus) assertThrows(ResponseStatusException.class, r::run).getStatusCode();
	}
}
