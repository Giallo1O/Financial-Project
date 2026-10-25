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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import it.glknet.financial_manager.dto.GroupViews.ExpenseView;
import it.glknet.financial_manager.dto.GroupViews.Share;
import it.glknet.financial_manager.model.SharedExpense;
import it.glknet.financial_manager.model.SharedGroup;
import it.glknet.financial_manager.model.SharedGroupMember;
import it.glknet.financial_manager.model.SharedShare;
import it.glknet.financial_manager.repository.SharedExpenseRepository;
import it.glknet.financial_manager.repository.SharedGroupMemberRepository;
import it.glknet.financial_manager.repository.SharedGroupRepository;
import it.glknet.financial_manager.repository.SharedSettlementRepository;
import it.glknet.financial_manager.repository.SharedShareRepository;

class SharedExpenseServiceTest {

	private final SharedGroupRepository groups = mock(SharedGroupRepository.class);
	private final SharedGroupMemberRepository members = mock(SharedGroupMemberRepository.class);
	private final SharedExpenseRepository expenses = mock(SharedExpenseRepository.class);
	private final SharedShareRepository shares = mock(SharedShareRepository.class);
	private final SharedSettlementRepository settlements = mock(SharedSettlementRepository.class);
	private final SharedExpenseService service = new SharedExpenseService(groups, members, expenses, shares, settlements);

	private final UUID gid = UUID.randomUUID();
	private final UUID anna = UUID.randomUUID();
	private final UUID bob = UUID.randomUUID();
	private final UUID stranger = UUID.randomUUID();

	private SharedExpenseServiceTest() {
		when(groups.findById(gid)).thenReturn(Optional.of(SharedGroup.builder().id(gid).name("Casa").build()));
		when(members.existsByGroupIdAndUserId(any(), any())).thenReturn(false);
		when(members.existsByGroupIdAndUserId(gid, anna)).thenReturn(true);
		when(members.existsByGroupIdAndUserId(gid, bob)).thenReturn(true);
		when(members.findByGroupIdOrderByJoinedAtAsc(gid)).thenReturn(List.of(
				SharedGroupMember.builder().groupId(gid).userId(anna).username("anna").build(),
				SharedGroupMember.builder().groupId(gid).userId(bob).username("bob").build()));
		when(settlements.save(any())).thenAnswer(i -> i.getArgument(0));
		when(expenses.save(any())).thenAnswer(i -> {
			SharedExpense e = i.getArgument(0);
			return SharedExpense.builder().id(UUID.randomUUID()).groupId(e.getGroupId()).paidBy(e.getPaidBy())
					.description(e.getDescription()).amount(e.getAmount()).expenseDate(e.getExpenseDate()).build();
		});
	}

	private static HttpStatus status(Runnable r) {
		return (HttpStatus) assertThrows(ResponseStatusException.class, r::run).getStatusCode();
	}

	@Test
	void expenseIsSplitEquallyAmongEveryoneByDefaultAndPaidByTheAuthor() {
		ExpenseView e = service.addExpense(anna, gid, "  cena ", new BigDecimal("10.01"), null, null, null);

		assertEquals("cena", e.description());
		assertEquals(anna, e.paidBy());
		assertEquals(LocalDate.now(), e.date());
		assertEquals(List.of(new Share(anna, new BigDecimal("5.01")), new Share(bob, new BigDecimal("5.00"))), e.shares());
	}

	@Test
	void onlyTheChosenParticipantsShareTheCost() {
		ExpenseView e = service.addExpense(anna, gid, "regalo per bob", new BigDecimal("20"), bob, null, List.of(anna));

		assertEquals(bob, e.paidBy());
		assertEquals(List.of(new Share(anna, new BigDecimal("20.00"))), e.shares());
	}

	@Test
	void invalidExpensesAreRejectedAndNothingIsSaved() {
		assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.addExpense(anna, gid, "x", BigDecimal.ZERO, null, null, null)));
		assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.addExpense(anna, gid, "x", new BigDecimal("0.001"), null, null, null)));
		assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.addExpense(anna, gid, "x", null, null, null, null)));
		assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.addExpense(anna, gid, "x", BigDecimal.TEN, stranger, null, null)));
		assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.addExpense(anna, gid, "x", BigDecimal.TEN, null, null, List.of(stranger))));
		verify(expenses, never()).save(any());
	}

	@Test
	void nonMembersSeeNothing() {
		assertEquals(HttpStatus.NOT_FOUND, status(() -> service.summary(stranger, gid)));
		assertEquals(HttpStatus.NOT_FOUND, status(() -> service.addExpense(stranger, gid, "x", BigDecimal.TEN, null, null, null)));
		assertEquals(HttpStatus.NOT_FOUND, status(() -> service.createInvite(stranger, gid)));
		assertEquals(HttpStatus.NOT_FOUND, status(() -> service.leave(stranger, gid)));
	}

	@Test
	void cannotLeaveWithOpenBalanceButCanWhenSettled() {
		SharedExpense paid = SharedExpense.builder().id(UUID.randomUUID()).groupId(gid).paidBy(anna)
				.amount(new BigDecimal("10")).expenseDate(LocalDate.now()).build();
		when(expenses.findByGroupIdOrderByExpenseDateDescCreatedAtDesc(gid)).thenReturn(List.of(paid));
		when(shares.findByExpenseIdIn(any())).thenReturn(List.of(
				SharedShare.builder().expenseId(paid.getId()).userId(anna).amount(new BigDecimal("5")).build(),
				SharedShare.builder().expenseId(paid.getId()).userId(bob).amount(new BigDecimal("5")).build()));

		assertEquals(HttpStatus.CONFLICT, status(() -> service.leave(bob, gid)));
		assertEquals(HttpStatus.CONFLICT, status(() -> service.leave(anna, gid)));
		verify(members, never()).deleteByGroupIdAndUserId(any(), any());

		service.settle(bob, gid, null, anna, new BigDecimal("5"), null);
		when(settlements.findByGroupIdOrderBySettleDateDescCreatedAtDesc(gid)).thenReturn(List.of(
				it.glknet.financial_manager.model.SharedSettlement.builder().fromUser(bob).toUser(anna)
						.amount(new BigDecimal("5")).settleDate(LocalDate.now()).build()));
		service.leave(bob, gid);
		verify(members).deleteByGroupIdAndUserId(gid, bob);
	}

	@Test
	void settlementsNeedTwoDifferentMembersAndAPositiveAmount() {
		assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.settle(anna, gid, null, anna, BigDecimal.TEN, null)));
		assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.settle(anna, gid, null, bob, BigDecimal.ZERO, null)));
		assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.settle(anna, gid, null, stranger, BigDecimal.TEN, null)));
	}
}
