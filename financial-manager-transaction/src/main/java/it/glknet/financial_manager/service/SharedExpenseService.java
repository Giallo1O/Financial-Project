package it.glknet.financial_manager.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import it.glknet.financial_manager.dto.GroupViews.Balance;
import it.glknet.financial_manager.dto.GroupViews.ExpenseView;
import it.glknet.financial_manager.dto.GroupViews.GroupView;
import it.glknet.financial_manager.dto.GroupViews.Member;
import it.glknet.financial_manager.dto.GroupViews.SettlementView;
import it.glknet.financial_manager.dto.GroupViews.Share;
import it.glknet.financial_manager.dto.GroupViews.Summary;
import it.glknet.financial_manager.model.SharedExpense;
import it.glknet.financial_manager.model.SharedGroup;
import it.glknet.financial_manager.model.SharedGroupMember;
import it.glknet.financial_manager.model.SharedSettlement;
import it.glknet.financial_manager.model.SharedShare;
import it.glknet.financial_manager.repository.SharedExpenseRepository;
import it.glknet.financial_manager.repository.SharedGroupMemberRepository;
import it.glknet.financial_manager.repository.SharedGroupRepository;
import it.glknet.financial_manager.repository.SharedSettlementRepository;
import it.glknet.financial_manager.repository.SharedShareRepository;
import lombok.RequiredArgsConstructor;

/**
 * Spese in due (o in piu'): chi paga, chi partecipa, quanto deve ognuno. Le quote sono salvate al momento
 * della spesa, quindi i saldi non cambiano se qualcuno entra o esce dopo.
 */
@Service
@RequiredArgsConstructor
public class SharedExpenseService {

	private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int CODE_LENGTH = 8;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final SharedGroupRepository groups;
	private final SharedGroupMemberRepository members;
	private final SharedExpenseRepository expenses;
	private final SharedShareRepository shares;
	private final SharedSettlementRepository settlements;

	// ---------------------------------------------------------------- gruppi

	public List<GroupView> groups(UUID userId) {
		List<UUID> ids = members.findByUserId(userId).stream().map(SharedGroupMember::getGroupId).toList();
		return groups.findAllById(ids).stream()
				.sorted(Comparator.comparing(SharedGroup::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
				.map(g -> view(g, userId)).toList();
	}

	@Transactional
	public GroupView create(UUID userId, String username, String name) {
		String clean = name == null || name.isBlank() ? "Spese in due" : name.trim();
		SharedGroup g = groups.save(SharedGroup.builder().name(clean.length() > 60 ? clean.substring(0, 60) : clean).build());
		members.save(SharedGroupMember.builder().groupId(g.getId()).userId(userId).username(username).build());
		return view(g, userId);
	}

	@Transactional
	public GroupView join(UUID userId, String username, String code) {
		String clean = code == null ? "" : code.trim().toUpperCase().replaceAll("[\\s-]", "");
		SharedGroup g = clean.isEmpty() ? null : groups.findByInviteCode(clean).orElse(null);
		if (g == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Codice non valido");
		}
		if (!members.existsByGroupIdAndUserId(g.getId(), userId)) {
			members.save(SharedGroupMember.builder().groupId(g.getId()).userId(userId).username(username).build());
		}
		return view(g, userId);
	}

	/** Crea (o sostituisce) il codice di invito. Lo puo' fare qualsiasi membro. */
	@Transactional
	public String createInvite(UUID userId, UUID groupId) {
		SharedGroup g = accessible(userId, groupId);
		String code;
		do {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < CODE_LENGTH; i++) {
				sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
			}
			code = sb.toString();
		} while (groups.findByInviteCode(code).isPresent());
		g.setInviteCode(code);
		groups.save(g);
		return code;
	}

	@Transactional
	public void revokeInvite(UUID userId, UUID groupId) {
		SharedGroup g = accessible(userId, groupId);
		g.setInviteCode(null);
		groups.save(g);
	}

	/** Si esce solo a conti pareggiati; chi esce per ultimo elimina il gruppo con tutto lo storico. */
	@Transactional
	public void leave(UUID userId, UUID groupId) {
		SharedGroup g = accessible(userId, groupId);
		long mine = balances(groupId).getOrDefault(userId, 0L);
		if (mine != 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Prima pareggia i conti: ti risultano " + SplitMath.euros(mine).abs().toPlainString().replace('.', ',') + " euro "
							+ (mine > 0 ? "da ricevere" : "da dare"));
		}
		members.deleteByGroupIdAndUserId(groupId, userId);
		if (members.findByGroupIdOrderByJoinedAtAsc(groupId).isEmpty()) {
			List<UUID> expenseIds = expenses.findByGroupIdOrderByExpenseDateDescCreatedAtDesc(groupId).stream()
					.map(SharedExpense::getId).toList();
			shares.deleteByExpenseIdIn(expenseIds);
			expenses.deleteByGroupId(groupId);
			settlements.deleteByGroupId(groupId);
			groups.delete(g);
		}
	}

	// ---------------------------------------------------------------- spese

	public Summary summary(UUID userId, UUID groupId) {
		SharedGroup g = accessible(userId, groupId);
		List<SharedExpense> exps = expenses.findByGroupIdOrderByExpenseDateDescCreatedAtDesc(groupId);
		List<SharedShare> parts = shares.findByExpenseIdIn(exps.stream().map(SharedExpense::getId).toList());
		List<SharedSettlement> sets = settlements.findByGroupIdOrderBySettleDateDescCreatedAtDesc(groupId);
		Map<UUID, Long> net = SplitMath.balances(exps, parts, sets);

		Map<UUID, List<Share>> byExpense = new HashMap<>();
		parts.forEach(p -> byExpense.computeIfAbsent(p.getExpenseId(), k -> new ArrayList<>())
				.add(new Share(p.getUserId(), p.getAmount())));
		List<Member> people = members.findByGroupIdOrderByJoinedAtAsc(groupId).stream()
				.map(m -> new Member(m.getUserId(), m.getUsername())).toList();
		List<Balance> balances = people.stream()
				.map(m -> new Balance(m.userId(), SplitMath.euros(net.getOrDefault(m.userId(), 0L)))).toList();
		return new Summary(
				new GroupView(g.getId(), g.getName(), g.getInviteCode(), people,
						SplitMath.euros(net.getOrDefault(userId, 0L))),
				balances, SplitMath.simplify(net),
				exps.stream().map(e -> new ExpenseView(e.getId(), e.getDescription(), e.getAmount(), e.getPaidBy(),
						e.getExpenseDate(), byExpense.getOrDefault(e.getId(), List.of()))).toList(),
				sets.stream().map(s -> new SettlementView(s.getId(), s.getFromUser(), s.getToUser(), s.getAmount(),
						s.getSettleDate())).toList());
	}

	/** paidBy null = chi registra; participants null o vuoto = tutti i membri. */
	@Transactional
	public ExpenseView addExpense(UUID userId, UUID groupId, String description, BigDecimal amount, UUID paidBy,
			LocalDate date, List<UUID> participants) {
		accessible(userId, groupId);
		if (amount == null || amount.signum() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'importo deve essere positivo");
		}
		long cents = SplitMath.cents(amount);
		if (cents <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'importo deve essere almeno un centesimo");
		}
		List<UUID> everyone = members.findByGroupIdOrderByJoinedAtAsc(groupId).stream().map(SharedGroupMember::getUserId)
				.toList();
		UUID payer = paidBy == null ? userId : paidBy;
		if (!everyone.contains(payer)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chi ha pagato non fa parte del gruppo");
		}
		Set<UUID> given = participants == null ? Set.of()
				: participants.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
		Set<UUID> wanted = given.isEmpty() ? Set.copyOf(everyone) : given;
		if (!everyone.containsAll(wanted)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uno dei partecipanti non fa parte del gruppo");
		}
		List<UUID> ordered = everyone.stream().filter(wanted::contains).toList();
		String text = description == null || description.isBlank() ? "Spesa" : description.trim();

		SharedExpense e = expenses.save(SharedExpense.builder().groupId(groupId).paidBy(payer).createdBy(userId)
				.description(text.length() > 100 ? text.substring(0, 100) : text).amount(SplitMath.euros(cents))
				.expenseDate(date == null ? LocalDate.now() : date).build());
		List<Share> saved = new ArrayList<>();
		SplitMath.split(cents, ordered).forEach((user, part) -> {
			shares.save(SharedShare.builder().expenseId(e.getId()).userId(user).amount(SplitMath.euros(part)).build());
			saved.add(new Share(user, SplitMath.euros(part)));
		});
		return new ExpenseView(e.getId(), e.getDescription(), e.getAmount(), e.getPaidBy(), e.getExpenseDate(), saved);
	}

	@Transactional
	public void deleteExpense(UUID userId, UUID groupId, UUID expenseId) {
		accessible(userId, groupId);
		SharedExpense e = expenses.findById(expenseId).filter(x -> x.getGroupId().equals(groupId))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Spesa non trovata"));
		shares.deleteByExpenseIdIn(List.of(e.getId()));
		expenses.delete(e);
	}

	// ---------------------------------------------------------------- pareggi

	/** from null = chi registra. */
	@Transactional
	public SettlementView settle(UUID userId, UUID groupId, UUID from, UUID to, BigDecimal amount, LocalDate date) {
		accessible(userId, groupId);
		UUID giver = from == null ? userId : from;
		if (to == null || giver.equals(to)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scegli due persone diverse");
		}
		if (amount == null || amount.signum() <= 0 || SplitMath.cents(amount) <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'importo deve essere positivo");
		}
		if (!members.existsByGroupIdAndUserId(groupId, giver) || !members.existsByGroupIdAndUserId(groupId, to)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entrambe le persone devono far parte del gruppo");
		}
		SharedSettlement s = settlements.save(SharedSettlement.builder().groupId(groupId).fromUser(giver).toUser(to)
				.amount(SplitMath.euros(SplitMath.cents(amount))).settleDate(date == null ? LocalDate.now() : date).build());
		return new SettlementView(s.getId(), s.getFromUser(), s.getToUser(), s.getAmount(), s.getSettleDate());
	}

	@Transactional
	public void deleteSettlement(UUID userId, UUID groupId, UUID settlementId) {
		accessible(userId, groupId);
		SharedSettlement s = settlements.findById(settlementId).filter(x -> x.getGroupId().equals(groupId))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pareggio non trovato"));
		settlements.delete(s);
	}

	// ---------------------------------------------------------------- dettagli

	private SharedGroup accessible(UUID userId, UUID groupId) {
		return groups.findById(groupId).filter(g -> members.existsByGroupIdAndUserId(g.getId(), userId))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gruppo non trovato"));
	}

	private Map<UUID, Long> balances(UUID groupId) {
		List<SharedExpense> exps = expenses.findByGroupIdOrderByExpenseDateDescCreatedAtDesc(groupId);
		Collection<UUID> ids = exps.stream().map(SharedExpense::getId).toList();
		return SplitMath.balances(exps, shares.findByExpenseIdIn(ids),
				settlements.findByGroupIdOrderBySettleDateDescCreatedAtDesc(groupId));
	}

	private GroupView view(SharedGroup g, UUID userId) {
		List<Member> people = members.findByGroupIdOrderByJoinedAtAsc(g.getId()).stream()
				.map(m -> new Member(m.getUserId(), m.getUsername())).toList();
		return new GroupView(g.getId(), g.getName(), g.getInviteCode(), people,
				SplitMath.euros(balances(g.getId()).getOrDefault(userId, 0L)));
	}
}
