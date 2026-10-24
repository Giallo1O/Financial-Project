package it.glknet.financial_manager.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import it.glknet.financial_manager.dto.PriceHint;
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
import lombok.RequiredArgsConstructor;

/**
 * Liste della spesa condivisibili con un codice, con suggerimenti e prezzi presi dagli scontrini di chi guarda
 * (gli scontrini restano personali: ognuno vede i prezzi dei propri acquisti).
 */
@Service
@RequiredArgsConstructor
public class ShoppingService {

	private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // senza 0/O/1/I: si legge e si detta meglio
	private static final int CODE_LENGTH = 8;
	private static final LocalDate MIN = LocalDate.of(1970, 1, 1);
	private static final LocalDate MAX = LocalDate.of(2999, 12, 31);
	private static final int MAX_SUGGESTIONS = 40;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final ShoppingListRepository lists;
	private final ShoppingListMemberRepository members;
	private final ShoppingItemRepository items;
	private final ReceiptItemRepository receiptItems;
	private final ReceiptService receiptService;

	// ---------------------------------------------------------------- liste

	public List<ShoppingListView> lists(UUID userId) {
		Map<UUID, ShoppingList> mine = new LinkedHashMap<>();
		lists.findByOwnerIdOrderByCreatedAtAsc(userId).forEach(l -> mine.put(l.getId(), l));
		List<UUID> joined = members.findByUserId(userId).stream().map(ShoppingListMember::getListId).toList();
		lists.findAllById(joined).stream()
				.sorted(Comparator.comparing(ShoppingList::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
				.forEach(l -> mine.putIfAbsent(l.getId(), l));
		return mine.values().stream().map(l -> view(l, userId)).toList();
	}

	@Transactional
	public ShoppingListView create(UUID userId, String username, String name) {
		String clean = name == null || name.isBlank() ? "Spesa" : name.trim();
		ShoppingList list = lists.save(ShoppingList.builder().ownerId(userId).ownerName(username)
				.name(clean.length() > 60 ? clean.substring(0, 60) : clean).build());
		return view(list, userId);
	}

	/** Il proprietario elimina la lista (con le sue voci); chi partecipa soltanto ne esce. */
	@Transactional
	public void deleteOrLeave(UUID userId, UUID listId) {
		ShoppingList list = accessible(userId, listId);
		if (list.getOwnerId().equals(userId)) {
			items.deleteByListId(listId);
			members.deleteByListId(listId);
			lists.delete(list);
		} else {
			members.deleteByListIdAndUserId(listId, userId);
		}
	}

	/** Crea (o sostituisce) il codice con cui un'altra persona puo' entrare nella lista. Solo il proprietario. */
	@Transactional
	public String createInvite(UUID userId, UUID listId) {
		ShoppingList list = owned(userId, listId);
		String code;
		do {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < CODE_LENGTH; i++) {
				sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
			}
			code = sb.toString();
		} while (lists.findByInviteCode(code).isPresent());
		list.setInviteCode(code);
		lists.save(list);
		return code;
	}

	/** Disattiva la condivisione: il codice smette di funzionare (chi e' gia' dentro resta). */
	@Transactional
	public void revokeInvite(UUID userId, UUID listId) {
		ShoppingList list = owned(userId, listId);
		list.setInviteCode(null);
		lists.save(list);
	}

	@Transactional
	public ShoppingListView join(UUID userId, String username, String code) {
		String clean = code == null ? "" : code.trim().toUpperCase().replaceAll("[\\s-]", "");
		ShoppingList list = clean.isEmpty() ? null : lists.findByInviteCode(clean).orElse(null);
		if (list == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Codice non valido");
		}
		if (!list.getOwnerId().equals(userId) && !members.existsByListIdAndUserId(list.getId(), userId)) {
			members.save(ShoppingListMember.builder().listId(list.getId()).userId(userId).username(username).build());
		}
		return view(list, userId);
	}

	// ---------------------------------------------------------------- voci

	public List<ShoppingItemView> items(UUID userId, UUID listId) {
		accessible(userId, listId);
		Map<String, ProductSummary> known = knownProducts(userId);
		return items.findByListId(listId).stream()
				.sorted(Comparator.comparing(ShoppingItem::isChecked)
						.thenComparing(i -> i.isChecked() ? null : i.getCreatedAt(),
								Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(ShoppingItem::getCheckedAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.map(i -> itemView(i, known)).toList();
	}

	/** Aggiunge una voce; se c'e' gia' una voce da comprare con lo stesso nome e unita' ne aumenta la quantita'. */
	@Transactional
	public ShoppingItemView addItem(UUID userId, String username, UUID listId, String name, BigDecimal quantity,
			String unit) {
		accessible(userId, listId);
		String clean = name == null ? "" : name.trim();
		if (clean.isEmpty() || clean.length() > 80) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serve un nome (massimo 80 caratteri)");
		}
		BigDecimal qty = quantity == null ? BigDecimal.ONE : quantity;
		if (qty.signum() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La quantita' deve essere positiva");
		}
		String u = cleanUnit(unit);
		String key = TextKeywords.normalize(clean);
		ShoppingItem item = items.findByListId(listId).stream()
				.filter(i -> !i.isChecked() && TextKeywords.normalize(i.getName()).equals(key) && i.getUnit().equals(u))
				.findFirst().orElse(null);
		if (item != null) {
			item.setQuantity(item.getQuantity().add(qty));
		} else {
			item = ShoppingItem.builder().listId(listId).name(clean).quantity(qty).unit(u).addedBy(username).build();
		}
		return itemView(items.save(item), knownProducts(userId));
	}

	/** Cambia lo stato "preso" e/o la quantita' (i campi null non cambiano). */
	@Transactional
	public ShoppingItemView update(UUID userId, UUID listId, UUID itemId, Boolean checked, BigDecimal quantity) {
		accessible(userId, listId);
		ShoppingItem item = item(listId, itemId);
		if (checked != null && checked != item.isChecked()) {
			item.setChecked(checked);
			item.setCheckedAt(checked ? LocalDateTime.now() : null);
		}
		if (quantity != null) {
			if (quantity.signum() <= 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La quantita' deve essere positiva");
			}
			item.setQuantity(quantity);
		}
		return itemView(items.save(item), knownProducts(userId));
	}

	@Transactional
	public void deleteItem(UUID userId, UUID listId, UUID itemId) {
		accessible(userId, listId);
		items.delete(item(listId, itemId));
	}

	@Transactional
	public void clearChecked(UUID userId, UUID listId) {
		accessible(userId, listId);
		items.deleteByListIdAndCheckedTrue(listId);
	}

	// ---------------------------------------------------------------- suggerimenti

	public List<ShoppingSuggestion> suggestions(UUID userId) {
		return suggestions(userId, LocalDate.now());
	}

	/**
	 * Prodotti che compri di solito, dai tuoi scontrini. Per ognuno: quante volte (giorni diversi), ogni quanti giorni
	 * in media e da quanto non lo compri. "due" se sono passati almeno l'80% dei giorni abituali tra due acquisti.
	 * Prima quelli da ricomprare (i piu' in ritardo per primi), poi i piu' frequenti.
	 */
	public List<ShoppingSuggestion> suggestions(UUID userId, LocalDate today) {
		Map<String, List<ProductRow>> byProduct = receiptItems.findRows(userId, MIN, MAX).stream()
				.filter(r -> r.paidUnitPrice() != null && r.name() != null)
				.collect(Collectors.groupingBy(r -> productKey(r.name(), r.unit()), LinkedHashMap::new,
						Collectors.toList()));
		List<ShoppingSuggestion> result = new ArrayList<>();
		for (List<ProductRow> rows : byProduct.values()) {
			rows.sort(Comparator.comparing(ProductRow::purchaseDate));
			TreeSet<LocalDate> days = rows.stream().map(ProductRow::purchaseDate).collect(Collectors.toCollection(TreeSet::new));
			LocalDate first = days.first();
			LocalDate last = days.last();
			ProductRow latest = rows.get(rows.size() - 1);
			ProductRow cheapest = rows.stream().min(Comparator.comparing(ProductRow::paidUnitPrice)).orElse(latest);
			Integer avg = days.size() >= 2
					? (int) Math.round((double) ChronoUnit.DAYS.between(first, last) / (days.size() - 1)) : null;
			long since = Math.max(0, ChronoUnit.DAYS.between(last, today));
			boolean due = avg != null && avg > 0 && since >= 0.8 * avg;
			result.add(new ShoppingSuggestion(latest.name(), latest.unit(), latest.foodCategory(), days.size(), last, avg,
					since, due, latest.paidUnitPrice(), cheapest.paidUnitPrice(), cheapest.storeName()));
		}
		result.sort(Comparator.comparing(ShoppingSuggestion::due).reversed()
				.thenComparing(s -> s.due() ? -(double) s.daysSinceLast() / s.avgIntervalDays() : 0.0)
				.thenComparing(Comparator.comparingInt(ShoppingSuggestion::purchases).reversed())
				.thenComparing(ShoppingSuggestion::name, String.CASE_INSENSITIVE_ORDER));
		return result.stream().limit(MAX_SUGGESTIONS).toList();
	}

	// ---------------------------------------------------------------- dettagli

	private ShoppingList accessible(UUID userId, UUID listId) {
		return lists.findById(listId)
				.filter(l -> l.getOwnerId().equals(userId) || members.existsByListIdAndUserId(listId, userId))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lista non trovata"));
	}

	private ShoppingList owned(UUID userId, UUID listId) {
		ShoppingList list = accessible(userId, listId);
		if (!list.getOwnerId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo chi ha creato la lista puo' condividerla");
		}
		return list;
	}

	private ShoppingItem item(UUID listId, UUID itemId) {
		return items.findById(itemId).filter(i -> i.getListId().equals(listId))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Voce non trovata"));
	}

	private ShoppingListView view(ShoppingList l, UUID userId) {
		boolean mine = l.getOwnerId().equals(userId);
		List<String> people = new ArrayList<>();
		people.add(l.getOwnerName() == null ? "?" : l.getOwnerName());
		members.findByListId(l.getId()).forEach(m -> people.add(m.getUsername() == null ? "?" : m.getUsername()));
		List<ShoppingItem> all = items.findByListId(l.getId());
		return new ShoppingListView(l.getId(), l.getName(), l.getOwnerName(), mine, people,
				mine ? l.getInviteCode() : null, (int) all.stream().filter(i -> !i.isChecked()).count(), all.size());
	}

	private ShoppingItemView itemView(ShoppingItem i, Map<String, ProductSummary> known) {
		ProductSummary p = known.get(TextKeywords.normalize(i.getName()));
		// il prezzo vale solo se l'unita' coincide: 2 pz di banane non si stimano con il prezzo al kg
		PriceHint hint = p != null && Objects.equals(p.unit(), i.getUnit())
				? new PriceHint(p.lastUnitPrice(), p.minUnitPrice(), p.cheapestStore(), p.lastStore()) : null;
		return new ShoppingItemView(i.getId(), i.getName(), i.getQuantity(), i.getUnit(), i.isChecked(),
				i.getAddedBy(), hint);
	}

	/** Prodotti dai tuoi scontrini per nome normalizzato; con piu' unita' si tiene quella piu' comprata. */
	private Map<String, ProductSummary> knownProducts(UUID userId) {
		Map<String, ProductSummary> byName = new HashMap<>();
		for (ProductSummary p : receiptService.products(userId)) {
			byName.merge(TextKeywords.normalize(p.name()), p,
					(a, b) -> b.purchases() > a.purchases() ? b : a);
		}
		return byName;
	}

	private static String productKey(String name, String unit) {
		return TextKeywords.normalize(name) + "|" + (unit == null ? "" : unit.trim().toLowerCase());
	}

	private static String cleanUnit(String unit) {
		String u = unit == null ? "" : unit.trim().toLowerCase();
		return u.isEmpty() || u.length() > 10 ? "pz" : u;
	}
}
