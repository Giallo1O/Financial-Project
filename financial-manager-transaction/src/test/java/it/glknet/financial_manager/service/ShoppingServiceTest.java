package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import it.glknet.financial_manager.dto.ProductRow;
import it.glknet.financial_manager.dto.ProductSummary;
import it.glknet.financial_manager.dto.ShoppingItemView;
import it.glknet.financial_manager.dto.ShoppingListView;
import it.glknet.financial_manager.dto.ShoppingSuggestion;
import it.glknet.financial_manager.model.ShoppingItem;
import it.glknet.financial_manager.model.ShoppingList;
import it.glknet.financial_manager.model.ShoppingListMember;
import it.glknet.financial_manager.repository.ReceiptItemRepository;
import it.glknet.financial_manager.repository.ShoppingItemRepository;
import it.glknet.financial_manager.repository.ShoppingListMemberRepository;
import it.glknet.financial_manager.repository.ShoppingListRepository;

class ShoppingServiceTest {

	private final Map<UUID, ShoppingList> listStore = new HashMap<>();
	private final List<ShoppingListMember> memberStore = new ArrayList<>();
	private final Map<UUID, ShoppingItem> itemStore = new HashMap<>();

	private final ShoppingListRepository lists = mock(ShoppingListRepository.class);
	private final ShoppingListMemberRepository members = mock(ShoppingListMemberRepository.class);
	private final ShoppingItemRepository items = mock(ShoppingItemRepository.class);
	private final ReceiptItemRepository receiptItems = mock(ReceiptItemRepository.class);
	private final ReceiptService receiptService = mock(ReceiptService.class);
	private final ShoppingService service = new ShoppingService(lists, members, items, receiptItems, receiptService);

	private final UUID andrea = UUID.randomUUID();
	private final UUID giulia = UUID.randomUUID();
	private final UUID stranger = UUID.randomUUID();

	ShoppingServiceTest() {
		// archivio in memoria al posto del database
		when(lists.save(any())).thenAnswer(i -> {
			ShoppingList l = i.getArgument(0);
			if (l.getId() == null) {
				l.setId(UUID.randomUUID());
				l.setCreatedAt(LocalDateTime.now());
			}
			listStore.put(l.getId(), l);
			return l;
		});
		when(lists.findById(any())).thenAnswer(i -> Optional.ofNullable(listStore.get(i.<UUID>getArgument(0))));
		when(lists.findByOwnerIdOrderByCreatedAtAsc(any())).thenAnswer(
				i -> listStore.values().stream().filter(l -> l.getOwnerId().equals(i.getArgument(0))).toList());
		when(lists.findByInviteCode(any())).thenAnswer(i -> listStore.values().stream()
				.filter(l -> i.getArgument(0).equals(l.getInviteCode())).findFirst());
		when(lists.findAllById(any())).thenAnswer(i -> {
			List<ShoppingList> found = new ArrayList<>();
			for (UUID id : i.<Iterable<UUID>>getArgument(0)) {
				if (listStore.containsKey(id)) {
					found.add(listStore.get(id));
				}
			}
			return found;
		});
		doAnswer(i -> listStore.remove(i.<ShoppingList>getArgument(0).getId())).when(lists).delete(any());
		doAnswer(i -> memberStore.removeIf(m -> m.getListId().equals(i.getArgument(0)) && m.getUserId().equals(i.getArgument(1))))
				.when(members).deleteByListIdAndUserId(any(), any());
		doAnswer(i -> memberStore.removeIf(m -> m.getListId().equals(i.getArgument(0)))).when(members).deleteByListId(any());
		doAnswer(i -> itemStore.values().removeIf(it -> it.getListId().equals(i.getArgument(0)))).when(items).deleteByListId(any());
		doAnswer(i -> itemStore.values().removeIf(it -> it.getListId().equals(i.getArgument(0)) && it.isChecked()))
				.when(items).deleteByListIdAndCheckedTrue(any());
		doAnswer(i -> itemStore.remove(i.<ShoppingItem>getArgument(0).getId())).when(items).delete(any());
		when(members.save(any())).thenAnswer(i -> {
			memberStore.add(i.getArgument(0));
			return i.getArgument(0);
		});
		when(members.existsByListIdAndUserId(any(), any())).thenAnswer(i -> memberStore.stream()
				.anyMatch(m -> m.getListId().equals(i.getArgument(0)) && m.getUserId().equals(i.getArgument(1))));
		when(members.findByListId(any())).thenAnswer(
				i -> memberStore.stream().filter(m -> m.getListId().equals(i.getArgument(0))).toList());
		when(members.findByUserId(any())).thenAnswer(
				i -> memberStore.stream().filter(m -> m.getUserId().equals(i.getArgument(0))).toList());
		when(items.save(any())).thenAnswer(i -> {
			ShoppingItem it = i.getArgument(0);
			if (it.getId() == null) {
				it.setId(UUID.randomUUID());
				it.setCreatedAt(LocalDateTime.now());
			}
			itemStore.put(it.getId(), it);
			return it;
		});
		when(items.findById(any())).thenAnswer(i -> Optional.ofNullable(itemStore.get(i.<UUID>getArgument(0))));
		when(items.findByListId(any())).thenAnswer(
				i -> itemStore.values().stream().filter(it -> it.getListId().equals(i.getArgument(0))).toList());
		when(receiptService.products(any())).thenReturn(List.of());
		when(receiptItems.findRows(any(), any(), any())).thenReturn(List.of());
	}

	// ---- liste, accesso e condivisione

	@Test
	void ownerSharesWithACodeAndTheOtherPersonCanUseTheList() {
		UUID listId = service.create(andrea, "andrea", "Spesa").id();
		ShoppingItemView latte = service.addItem(andrea, "andrea", listId, "Latte", null, null);

		assertThrows(ResponseStatusException.class, () -> service.items(giulia, listId)); // senza codice: 404

		String code = service.createInvite(andrea, listId);
		assertEquals(8, code.length());
		ShoppingListView joined = service.join(giulia, "giulia", code.toLowerCase()); // il codice non distingue le maiuscole

		assertFalse(joined.mine());
		assertNull(joined.inviteCode()); // il codice lo vede solo il proprietario
		assertEquals(List.of("andrea", "giulia"), joined.members());
		assertEquals(1, service.items(giulia, listId).size());
		service.update(giulia, listId, latte.id(), true, null);
		assertTrue(service.items(andrea, listId).get(0).checked()); // la spunta si vede da entrambi
		assertEquals(code, service.lists(andrea).get(0).inviteCode());
		assertEquals(1, service.lists(giulia).size());
	}

	@Test
	void joinWithWrongOrRevokedCodeFailsAndJoiningTwiceIsHarmless() {
		UUID listId = service.create(andrea, "andrea", "Spesa").id();
		String code = service.createInvite(andrea, listId);

		assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.join(giulia, "giulia", "XXXXXXXX"))
				.getStatusCode().value());
		service.join(giulia, "giulia", code);
		service.join(giulia, "giulia", code);
		service.join(andrea, "andrea", code); // il proprietario non diventa anche "membro"
		assertEquals(2, service.lists(andrea).get(0).members().size());

		service.revokeInvite(andrea, listId);
		assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.join(stranger, "x", code))
				.getStatusCode().value());
		assertEquals(1, service.lists(giulia).size()); // chi era gia' dentro resta
	}

	@Test
	void onlyTheOwnerCanShareAndStrangersSeeNothing() {
		UUID listId = service.create(andrea, "andrea", "Spesa").id();
		service.join(giulia, "giulia", service.createInvite(andrea, listId));

		assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.createInvite(giulia, listId))
				.getStatusCode().value());
		assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.addItem(stranger, "x", listId, "Pane", null, null))
				.getStatusCode().value());
		assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.clearChecked(stranger, listId))
				.getStatusCode().value());
		assertTrue(service.lists(stranger).isEmpty());
	}

	@Test
	void ownerDeletesTheListButAMemberOnlyLeaves() {
		UUID listId = service.create(andrea, "andrea", "Spesa").id();
		service.join(giulia, "giulia", service.createInvite(andrea, listId));

		service.deleteOrLeave(giulia, listId);
		assertTrue(service.lists(giulia).isEmpty());
		assertEquals(1, service.lists(andrea).size()); // la lista di andrea e' ancora li'

		service.deleteOrLeave(andrea, listId);
		assertTrue(service.lists(andrea).isEmpty());
	}

	// ---- voci

	@Test
	void addingTheSameItemAgainRaisesTheQuantityUnlessAlreadyTakenOrDifferentUnit() {
		UUID listId = service.create(andrea, "andrea", "Spesa").id();
		service.addItem(andrea, "andrea", listId, "Latte", BigDecimal.ONE, "pz");
		ShoppingItemView again = service.addItem(andrea, "andrea", listId, " latte ", new BigDecimal("2"), "pz");

		assertEquals(0, new BigDecimal("3").compareTo(again.quantity()));
		assertEquals(1, service.items(andrea, listId).size());

		service.addItem(andrea, "andrea", listId, "Latte", BigDecimal.ONE, "l"); // altra unita': voce separata
		service.update(andrea, listId, again.id(), true, null);
		service.addItem(andrea, "andrea", listId, "Latte", BigDecimal.ONE, "pz"); // la prima e' gia' presa: ne serve una nuova
		assertEquals(3, service.items(andrea, listId).size());
	}

	@Test
	void uncheckedItemsComeFirstAndClearCheckedRemovesTakenOnes() {
		UUID listId = service.create(andrea, "andrea", "Spesa").id();
		ShoppingItemView pane = service.addItem(andrea, "andrea", listId, "Pane", null, null);
		service.addItem(andrea, "andrea", listId, "Uova", null, null);
		service.update(andrea, listId, pane.id(), true, null);

		assertEquals(List.of("Uova", "Pane"), service.items(andrea, listId).stream().map(ShoppingItemView::name).toList());
		service.clearChecked(andrea, listId);
		assertEquals(List.of("Uova"), service.items(andrea, listId).stream().map(ShoppingItemView::name).toList());
	}

	@Test
	void rejectsBlankNamesAndNonPositiveQuantities() {
		UUID listId = service.create(andrea, "andrea", "Spesa").id();
		assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.addItem(andrea, "a", listId, "  ", null, null)).getStatusCode().value());
		assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.addItem(andrea, "a", listId, "Pane", BigDecimal.ZERO, null)).getStatusCode().value());
		ShoppingItemView pane = service.addItem(andrea, "a", listId, "Pane", null, null);
		assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.update(andrea, listId, pane.id(), null, new BigDecimal("-1"))).getStatusCode().value());
	}

	@Test
	void priceHintAppearsOnlyWhenNameAndUnitMatchAKnownProduct() {
		when(receiptService.products(andrea)).thenReturn(List.of(new ProductSummary("Banane", "kg", "Frutta e verdura", 3,
				new BigDecimal("1.99"), new BigDecimal("1.69"), new BigDecimal("1.80"), new BigDecimal("1.99"),
				LocalDate.of(2026, 10, 5), "Esselunga", "Lidl")));
		UUID listId = service.create(andrea, "andrea", "Spesa").id();
		service.addItem(andrea, "andrea", listId, "banane", new BigDecimal("0.5"), "kg");
		service.addItem(andrea, "andrea", listId, "Banane", BigDecimal.ONE, "pz"); // unita' diversa: nessuna stima
		service.addItem(andrea, "andrea", listId, "Pane", null, null);

		Map<String, ShoppingItemView> byKey = new HashMap<>();
		service.items(andrea, listId).forEach(i -> byKey.put(i.name() + "/" + i.unit(), i));

		assertEquals("Lidl", byKey.get("banane/kg").price().cheapestStore());
		assertEquals(0, new BigDecimal("1.69").compareTo(byKey.get("banane/kg").price().minUnitPrice()));
		assertNull(byKey.get("Banane/pz").price());
		assertNull(byKey.get("Pane/pz").price());
	}

	// ---- suggerimenti

	private static ProductRow row(String name, String price, String date, String store) {
		return new ProductRow(name, "pz", "Latticini e uova", BigDecimal.ONE, new BigDecimal(price), new BigDecimal(price),
				LocalDate.parse(date), store);
	}

	@Test
	void suggestionsFlagWhatIsDueAndOrderThem() {
		when(receiptItems.findRows(any(), any(), any())).thenReturn(List.of(
				row("Latte 1L", "1.40", "2026-10-01", "Coop"), row("latte  1l", "1.20", "2026-10-08", "Lidl"),
				row("Latte 1L", "1.60", "2026-10-15", "Esselunga"),
				row("Latte 1L", "1.60", "2026-10-15", "Esselunga"), // stesso giorno: conta come un solo acquisto
				row("Sconto: Latte 1L", "-0.20", "2026-10-15", "Esselunga"), // sconti: non sono prodotti
				row("Pasta", "1.00", "2026-08-01", "Coop"), row("Pasta", "1.10", "2026-09-01", "Coop"),
				row("Pasta", "1.05", "2026-10-01", "Coop"),
				row("Banane", "1.99", "2026-10-20", "Lidl")));

		List<ShoppingSuggestion> s = service.suggestions(andrea, LocalDate.parse("2026-10-23"));

		ShoppingSuggestion latte = s.stream().filter(x -> x.name().toLowerCase().startsWith("latte")).findFirst().orElseThrow();
		assertEquals(3, latte.purchases());
		assertEquals(7, latte.avgIntervalDays());
		assertEquals(8, latte.daysSinceLast()); // dal 15/10 al 23/10
		assertTrue(latte.due()); // 8 giorni >= 80% di 7
		assertEquals("Lidl", latte.cheapestStore());
		assertEquals(0, new BigDecimal("1.20").compareTo(latte.minUnitPrice()));
		assertEquals(0, new BigDecimal("1.60").compareTo(latte.lastUnitPrice()));

		ShoppingSuggestion pasta = s.stream().filter(x -> x.name().equals("Pasta")).findFirst().orElseThrow();
		assertEquals(31, pasta.avgIntervalDays());
		assertEquals(22, pasta.daysSinceLast());
		assertFalse(pasta.due()); // 22 giorni sono meno dell'80% di 31

		ShoppingSuggestion banane = s.stream().filter(x -> x.name().equals("Banane")).findFirst().orElseThrow();
		assertNull(banane.avgIntervalDays()); // un solo acquisto: non si puo' dire ogni quanto
		assertFalse(banane.due());

		assertEquals("Latte 1L", s.get(0).name()); // quello da ricomprare viene per primo
		assertEquals(List.of("Latte 1L", "Pasta", "Banane"), s.stream().map(ShoppingSuggestion::name).toList()); // poi i piu' frequenti
	}
}
