package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import it.glknet.financial_manager.dto.TransactionRequest;
import it.glknet.financial_manager.model.Category;
import it.glknet.financial_manager.model.CategoryRule;
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.model.TransactionType;
import it.glknet.financial_manager.repository.CategoryRepository;
import it.glknet.financial_manager.repository.CategoryRuleRepository;
import it.glknet.financial_manager.repository.TransactionRepository;

class CategoryRuleServiceTest {

	private final CategoryRuleRepository rules = mock(CategoryRuleRepository.class);
	private final CategoryRepository categories = mock(CategoryRepository.class);
	private final TransactionRepository transactions = mock(TransactionRepository.class);
	private final CategoryRuleService service = new CategoryRuleService(rules, categories, transactions);
	private final UUID user = UUID.randomUUID();

	private final Category shopping = category(true);
	private final Category subscriptions = category(true);
	private final Category salary = category(false);

	private Category category(boolean expense) {
		return Category.builder().id(UUID.randomUUID()).userId(user).name("c").expense(expense).build();
	}

	private CategoryRule rule(String keyword, Category c) {
		return CategoryRule.builder().id(UUID.randomUUID()).userId(user).keyword(keyword).categoryId(c.getId()).build();
	}

	private TransactionRequest row(String description, TransactionType type, UUID categoryId) {
		return new TransactionRequest(description, BigDecimal.TEN, type, categoryId, user, LocalDate.of(2026, 10, 1));
	}

	private void givenRules(CategoryRule... existing) {
		when(categories.findByUserId(user)).thenReturn(List.of(shopping, subscriptions, salary));
		when(rules.findByUserId(user)).thenReturn(new ArrayList<>(List.of(existing)));
		when(rules.save(any())).thenAnswer(i -> i.getArgument(0));
	}

	@Test
	void suggestsTheMostSpecificRuleOfTheRightType() {
		givenRules(rule("amazon", shopping), rule("amazon prime", subscriptions), rule("stipendio", salary));

		var s = service.suggest(user, List.of(
				row("AMAZON PRIME VIDEO", TransactionType.USCITA, null),
				row("AMAZON MKTPLACE", TransactionType.USCITA, null),
				row("STIPENDIO OTTOBRE", TransactionType.USCITA, null), // categoria di entrata su una uscita: niente
				row("STIPENDIO OTTOBRE", TransactionType.ENTRATA, null),
				row("AMAZON", TransactionType.USCITA, shopping.getId()), // gia' categorizzata: non si tocca
				row("COOP", TransactionType.USCITA, null)));

		assertEquals(new CategoryRuleService.Suggestion(subscriptions.getId(), "amazon prime"), s.get(0));
		assertEquals(shopping.getId(), s.get(1).categoryId());
		assertNull(s.get(2));
		assertEquals(salary.getId(), s.get(3).categoryId());
		assertNull(s.get(4));
		assertNull(s.get(5));
	}

	@Test
	void learnCreatesARuleFromTheChosenCategory() {
		givenRules();

		int changed = service.learn(user, List.of(
				row("PAGAMENTO POS ESSELUNGA MILANO 1234", TransactionType.USCITA, shopping.getId()),
				row("PAGAMENTO POS ESSELUNGA ROMA 9876", TransactionType.USCITA, shopping.getId()))); // stessa regola

		ArgumentCaptor<CategoryRule> saved = ArgumentCaptor.forClass(CategoryRule.class);
		verify(rules).save(saved.capture());
		assertEquals("esselunga", saved.getValue().getKeyword());
		assertEquals(shopping.getId(), saved.getValue().getCategoryId());
		assertEquals(1, changed);
	}

	@Test
	void learnCorrectsTheRuleThatGaveAWrongSuggestion() {
		CategoryRule amazon = rule("amazon", shopping);
		givenRules(amazon);

		int changed = service.learn(user, List.of(row("AMAZON PRIME", TransactionType.USCITA, subscriptions.getId())));

		assertEquals(subscriptions.getId(), amazon.getCategoryId()); // la regola che sbagliava ora e' corretta
		assertEquals(1, changed);
	}

	@Test
	void learnIgnoresInvalidChoicesAndRowsWithoutCategory() {
		CategoryRule amazon = rule("amazon", shopping);
		givenRules(amazon);

		int changed = service.learn(user, List.of(
				row("NETFLIX", TransactionType.USCITA, salary.getId()), // categoria di entrata su un'uscita
				row("COOP", TransactionType.USCITA, UUID.randomUUID()), // categoria sconosciuta
				row("BAR", TransactionType.USCITA, null), // nessuna scelta
				row("AMAZON ORDINE", TransactionType.USCITA, shopping.getId()))); // gia' coerente con la regola

		assertEquals(0, changed);
		verify(rules, never()).save(any());
	}

	@Test
	void historyCreatesRulesOnlyForFrequentAndConsistentKeywords() {
		givenRules(rule("netflix", subscriptions)); // esiste gia': non si tocca
		Category coffee = category(true);
		when(categories.findByUserId(user)).thenReturn(List.of(shopping, subscriptions, salary, coffee));
		List<Transaction> history = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			history.add(tx("ESSELUNGA MILANO " + i, shopping));
		}
		history.add(tx("ESSELUNGA ROMA", coffee)); // 4 su 5 = 80%: basta
		history.add(tx("COOP", shopping)); // una sola volta: troppo poco
		history.add(tx("BAR TABACCHI", shopping));
		history.add(tx("BAR TABACCHI", coffee)); // 50/50: ambiguo
		history.add(tx("NETFLIX", subscriptions));
		history.add(tx("NETFLIX", subscriptions));
		when(transactions.findByUserId(user)).thenReturn(history);

		int created = service.learnFromHistory(user);

		ArgumentCaptor<CategoryRule> saved = ArgumentCaptor.forClass(CategoryRule.class);
		verify(rules).save(saved.capture());
		assertEquals(1, created);
		assertEquals("esselunga", saved.getValue().getKeyword());
		assertEquals(shopping.getId(), saved.getValue().getCategoryId());
	}

	@Test
	void savingRejectsShortKeywordsAndForeignCategories() {
		when(categories.findById(any(UUID.class))).thenAnswer(i -> i.getArgument(0).equals(shopping.getId())
				? Optional.of(shopping) : Optional.empty());

		assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.save(user, "  a! ", shopping.getId()))
				.getStatusCode().value());
		assertEquals(400, assertThrows(ResponseStatusException.class,
				() -> service.save(user, "coop", UUID.randomUUID())).getStatusCode().value());
		verify(rules, never()).save(any());
	}

	private Transaction tx(String description, Category c) {
		return Transaction.builder().id(UUID.randomUUID()).userId(user).description(description)
				.amount(BigDecimal.TEN).type(TransactionType.USCITA).categoryId(c.getId()).build();
	}

	private Transaction uncategorized(String description, TransactionType type, String amount) {
		return Transaction.builder().id(UUID.randomUUID()).userId(user).description(description)
				.amount(new BigDecimal(amount)).type(type).build();
	}

	@Test
	void applyingRulesCategorizesOnlyUncategorizedMovementsOfTheRightType() {
		givenRules(rule("bennet", shopping), rule("stipendio", salary));
		Transaction bennet = uncategorized("BENNET 12", TransactionType.USCITA, "31.40");
		Transaction salaryAsExpense = uncategorized("STIPENDIO", TransactionType.USCITA, "5"); // tipo sbagliato
		Transaction already = tx("BENNET 8", subscriptions); // ha gia' una categoria: resta
		Transaction unknown = uncategorized("COOP", TransactionType.USCITA, "9");
		when(transactions.findByUserId(user)).thenReturn(List.of(bennet, salaryAsExpense, already, unknown));

		assertEquals(1, service.applyToUncategorized(user));
		assertEquals(shopping.getId(), bennet.getCategoryId());
		assertNull(salaryAsExpense.getCategoryId());
		assertEquals(subscriptions.getId(), already.getCategoryId());
		assertNull(unknown.getCategoryId());
	}

	@Test
	void uncategorizedMovementsAreGroupedByMerchantAndType() {
		when(transactions.findByUserId(user)).thenReturn(List.of(
				uncategorized("BENNET 12", TransactionType.USCITA, "31.40"),
				uncategorized("BENNET 8", TransactionType.USCITA, "10.00"),
				uncategorized("Pagamento POS Bennet", TransactionType.USCITA, "5.00"),
				uncategorized("BENNET rimborso", TransactionType.ENTRATA, "3.00"),
				uncategorized("D+", TransactionType.USCITA, "2.00"),
				tx("BENNET 9", shopping)));

		var groups = service.uncategorized(user);

		assertEquals("bennet", groups.get(0).keyword());
		assertEquals(3, groups.get(0).count());
		assertEquals(0, new BigDecimal("46.40").compareTo(groups.get(0).total()));
		assertEquals(TransactionType.USCITA, groups.get(0).type());
		assertEquals(3, groups.size()); // bennet uscite, bennet entrate, "d"
	}
}
