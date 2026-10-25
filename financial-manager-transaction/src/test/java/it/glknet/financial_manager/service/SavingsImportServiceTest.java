package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import it.glknet.financial_manager.dto.TransactionRequest;
import it.glknet.financial_manager.model.SavingsGoal;
import it.glknet.financial_manager.model.SavingsMovement;
import it.glknet.financial_manager.model.TransactionType;
import it.glknet.financial_manager.repository.ImportSourceRepository;
import it.glknet.financial_manager.repository.SavingsGoalRepository;
import it.glknet.financial_manager.repository.SavingsMovementRepository;

class SavingsImportServiceTest {

	private final SavingsGoalRepository goals = mock(SavingsGoalRepository.class);
	private final SavingsMovementRepository movements = mock(SavingsMovementRepository.class);
	private final SavingsImportService service = new SavingsImportService(goals, movements,
			mock(ImportSourceRepository.class));
	private final UUID user = UUID.randomUUID();
	private final LocalDate day = LocalDate.of(2026, 10, 5);

	private final SavingsGoal bbva = SavingsGoal.builder().id(UUID.randomUUID()).userId(user).name("Risparmi BBVA")
			.targetAmount(new BigDecimal("10000")).currentAmount(new BigDecimal("1000")).sourceType("IBAN")
			.sourceKey("IT13X0000000000000000001724").build();
	private final SavingsGoal holidays = SavingsGoal.builder().id(UUID.randomUUID()).userId(user).name("Vacanze")
			.targetAmount(new BigDecimal("2000")).currentAmount(new BigDecimal("100")).sourceType("SPACE")
			.sourceKey("Vacanze").build();

	SavingsImportServiceTest() {
		when(goals.findByUserIdOrderByCreatedAtDesc(user)).thenReturn(List.of(bbva, holidays));
		when(goals.save(any())).thenAnswer(i -> i.getArgument(0));
	}

	private TransactionRequest row(String desc, String amount, TransactionType type) {
		return new TransactionRequest(desc, new BigDecimal(amount), type, null, user, day);
	}

	@Test
	void transfersToTheSavingsIbanAreDepositsAndPaymentsFromASpaceAreWithdrawals() {
		var result = service.forRows(user,
				List.of(row("Andrea Gallicchio", "300", TransactionType.USCITA),
						row("GALLICCHIO ANDREA", "50", TransactionType.ENTRATA),
						row("Andrea Gallicchio", "80", TransactionType.USCITA), // altro conto: non collegato
						row("BOOKING.COM", "420", TransactionType.USCITA),
						row("BENNET 12", "31.40", TransactionType.USCITA)),
				List.of(new CsvImportService.Source("Conto corrente principale", "Andrea Gallicchio",
						"IT13X0000000000000000001724"),
						new CsvImportService.Source("Conto corrente principale", "GALLICCHIO ANDREA",
								"IT13X0000000000000000001724"),
						new CsvImportService.Source("Conto corrente principale", "Andrea Gallicchio",
								"IT56X0000000000000000001476"),
						new CsvImportService.Source("Vacanze", "BOOKING.COM", ""),
						new CsvImportService.Source("Conto corrente principale", "BENNET 12", "")));

		assertEquals(0, new BigDecimal("300").compareTo(result.get(0).move().amount())); // versamento
		assertFalse(result.get(0).alsoTransaction()); // non e' una spesa
		assertEquals(0, new BigDecimal("-50").compareTo(result.get(1).move().amount())); // prelievo
		assertNull(result.get(2));
		assertEquals(holidays.getId(), result.get(3).move().goalId());
		assertEquals(0, new BigDecimal("-420").compareTo(result.get(3).move().amount()));
		assertTrue(result.get(3).alsoTransaction()); // la vacanza e' anche una spesa vera
		assertNull(result.get(4));
	}

	@Test
	void transfersBetweenMainAccountAndALinkedSpaceUseTheSpaceSide() {
		var moves = service.forInternal(user, List.of(
				new CsvImportService.Internal(day, new BigDecimal("-200"), "Conto corrente principale", "Vacanze"),
				new CsvImportService.Internal(day, new BigDecimal("200"), "Vacanze", "Conto corrente principale"),
				new CsvImportService.Internal(day, new BigDecimal("15"), "Abbonamenti", "Conto corrente principale")));

		assertEquals(1, moves.size());
		assertEquals(0, new BigDecimal("200").compareTo(moves.get(0).amount()));
		assertEquals(holidays.getId(), moves.get(0).goalId());
	}

	@Test
	void applyingTheSameImportTwiceCountsEachMovementOnce() {
		List<SavingsMovement> saved = new ArrayList<>();
		when(movements.findByUserId(user)).thenAnswer(i -> new ArrayList<>(saved));
		when(movements.save(any())).thenAnswer(i -> {
			saved.add(i.getArgument(0));
			return i.getArgument(0);
		});
		var moves = List.of(
				new SavingsImportService.SavingsMove(bbva.getId(), "Risparmi BBVA", day, new BigDecimal("300"), "Bonifico"),
				new SavingsImportService.SavingsMove(holidays.getId(), "Vacanze", day, new BigDecimal("-500"), "Hotel"));

		assertEquals(new SavingsImportService.ApplyResult(2, 0), service.apply(user, moves));
		assertEquals(new SavingsImportService.ApplyResult(0, 2), service.apply(user, moves));
		assertEquals(0, new BigDecimal("1300").compareTo(bbva.getCurrentAmount()));
		assertEquals(0, BigDecimal.ZERO.compareTo(holidays.getCurrentAmount())); // mai sotto zero
	}
}
