package it.glknet.financial_manager.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import it.glknet.financial_manager.dto.AmountOperationRequest;
import it.glknet.financial_manager.model.SavingsGoal;
import it.glknet.financial_manager.repository.SavingsGoalRepository;

class SavingsGoalControllerTest {

	private final SavingsGoalRepository repo = mock(SavingsGoalRepository.class);
	private final SavingsGoalController controller = new SavingsGoalController(repo,
			mock(it.glknet.financial_manager.service.SavingsImportService.class),
			mock(it.glknet.financial_manager.repository.SavingsMovementRepository.class));
	private final UUID owner = UUID.randomUUID();
	private final SavingsGoal goal = SavingsGoal.builder().id(UUID.randomUUID()).userId(owner).name("Vacanze")
			.targetAmount(new BigDecimal("100")).currentAmount(new BigDecimal("80")).build();

	SavingsGoalControllerTest() {
		when(repo.findById(goal.getId())).thenReturn(Optional.of(goal));
		when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
	}

	@Test
	void depositReachingTargetCompletesGoalAndWithdrawReopensIt() {
		controller.deposit(owner, goal.getId(), new AmountOperationRequest(new BigDecimal("20")));
		assertTrue(goal.isCompleted());

		controller.withdraw(owner, goal.getId(), new AmountOperationRequest(new BigDecimal("30")));
		assertFalse(goal.isCompleted());
		assertEquals(0, new BigDecimal("70").compareTo(goal.getCurrentAmount()));
	}

	@Test
	void withdrawingMoreThanSavedOrNonPositiveAmountsAreRejected() {
		for (BigDecimal bad : new BigDecimal[] { new BigDecimal("500"), BigDecimal.ZERO, new BigDecimal("-5") }) {
			org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
					() -> controller.withdraw(owner, goal.getId(), new AmountOperationRequest(bad)));
		}
		org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
				() -> controller.deposit(owner, goal.getId(), new AmountOperationRequest(new BigDecimal("-100"))));
		assertEquals(0, new BigDecimal("80").compareTo(goal.getCurrentAmount()));
	}

	@Test
	void otherUsersGoalIsNotFound() {
		var res = controller.deposit(UUID.randomUUID(), goal.getId(), new AmountOperationRequest(BigDecimal.TEN));
		assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
		assertEquals(0, new BigDecimal("80").compareTo(goal.getCurrentAmount()));
	}
}
