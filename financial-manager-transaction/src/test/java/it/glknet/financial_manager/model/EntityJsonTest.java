package it.glknet.financial_manager.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/** Il frontend non manda i campi boolean: devono valere i default e non andare in errore (Spring usa Jackson 3). */
class EntityJsonTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void savingsGoalWithoutCompleted() {
		SavingsGoal g = mapper.readValue("{\"name\":\"V\",\"targetAmount\":100,\"currentAmount\":0}", SavingsGoal.class);
		assertFalse(g.isCompleted());
		assertEquals("V", g.getName());
	}

	@Test
	void recurringWithoutActiveIsActive() {
		RecurringTransaction r = mapper.readValue(
				"{\"description\":\"A\",\"amount\":5,\"type\":\"USCITA\",\"dayOfMonth\":3,\"autoConfirm\":false}",
				RecurringTransaction.class);
		assertTrue(r.isActive());
		assertEquals(3, r.getDayOfMonth());
	}

	@Test
	void categoryWithoutFlagsIsAnExpense() {
		Category c = mapper.readValue("{\"name\":\"Spesa\"}", Category.class);
		assertTrue(c.isExpense());
		assertFalse(c.isFixed());
	}
}
