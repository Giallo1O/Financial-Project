package it.glknet.financial_manager.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import it.glknet.financial_manager.dto.AccountView;
import it.glknet.financial_manager.dto.AccountsOverview;
import it.glknet.financial_manager.model.Account;
import it.glknet.financial_manager.model.Transfer;
import it.glknet.financial_manager.model.TransactionType;
import it.glknet.financial_manager.repository.AccountRepository;
import it.glknet.financial_manager.repository.RecurringTransactionRepository;
import it.glknet.financial_manager.repository.TransactionRepository;
import it.glknet.financial_manager.repository.TransferRepository;
import lombok.RequiredArgsConstructor;

/** Conti e giroconti. I giroconti sono separati dalle transazioni: non compaiono mai come entrate o spese. */
@Service
@RequiredArgsConstructor
public class AccountService {

	private static final Set<String> TYPES = Set.of("CORRENTE", "CARTA", "CONTANTI", "RISPARMIO", "INVESTIMENTI", "ALTRO");

	private final AccountRepository accounts;
	private final TransferRepository transfers;
	private final TransactionRepository transactions;
	private final RecurringTransactionRepository recurring;

	public AccountsOverview overview(UUID userId) {
		return overview(userId, LocalDate.now());
	}

	/** Saldi alla data indicata: i movimenti e i giroconti successivi non contano ancora. */
	public AccountsOverview overview(UUID userId, LocalDate today) {
		Map<UUID, BigDecimal> flow = new HashMap<>();
		BigDecimal unassigned = BigDecimal.ZERO;
		for (Object[] row : transactions.sumsByAccount(userId, today)) {
			BigDecimal sum = (BigDecimal) row[2];
			BigDecimal signed = row[1] == TransactionType.ENTRATA ? sum : sum.negate();
			if (row[0] == null) {
				unassigned = unassigned.add(signed);
			} else {
				flow.merge((UUID) row[0], signed, BigDecimal::add);
			}
		}
		for (Transfer t : transfers.findByUserIdOrderByTransferDateDescCreatedAtDesc(userId)) {
			if (!t.getTransferDate().isAfter(today)) {
				flow.merge(t.getFromAccountId(), t.getAmount().negate(), BigDecimal::add);
				flow.merge(t.getToAccountId(), t.getAmount(), BigDecimal::add);
			}
		}
		List<AccountView> views = accounts.findByUserIdOrderByCreatedAtAsc(userId).stream().map(a -> {
			BigDecimal initial = a.getInitialBalance() == null ? BigDecimal.ZERO : a.getInitialBalance();
			return new AccountView(a.getId(), a.getName(), a.getType(), a.getColor(), initial,
					initial.add(flow.getOrDefault(a.getId(), BigDecimal.ZERO)), a.isArchived());
		}).toList();
		BigDecimal total = views.stream().map(AccountView::balance).reduce(unassigned, BigDecimal::add);
		boolean hasUnassigned = transactions.sumsByAccount(userId, LocalDate.of(2999, 12, 31)).stream()
				.anyMatch(r -> r[0] == null);
		return new AccountsOverview(views, unassigned, hasUnassigned, total);
	}

	@Transactional
	public Account create(UUID userId, String name, String type, BigDecimal initialBalance, String color) {
		Account a = Account.builder().userId(userId).build();
		fill(a, name, type, initialBalance, color);
		return accounts.save(a);
	}

	@Transactional
	public Account update(UUID userId, UUID id, String name, String type, BigDecimal initialBalance, String color,
			Boolean archived) {
		Account a = owned(userId, id);
		fill(a, name, type, initialBalance, color);
		if (archived != null) {
			a.setArchived(archived);
		}
		return accounts.save(a);
	}

	/** Elimina il conto; i suoi movimenti restano ma "senza conto". Con dei giroconti collegati non si puo'. */
	@Transactional
	public void delete(UUID userId, UUID id) {
		Account a = owned(userId, id);
		if (transfers.existsByFromAccountIdOrToAccountId(id, id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Il conto ha dei giroconti: eliminali prima oppure archivia il conto");
		}
		transactions.detachAccount(id);
		recurring.detachAccount(id);
		accounts.delete(a);
	}

	/** Mette sul conto indicato tutti i movimenti che non ne hanno uno; restituisce quanti. */
	@Transactional
	public int assignUnassigned(UUID userId, UUID accountId) {
		owned(userId, accountId);
		return transactions.assignUnassigned(userId, accountId);
	}

	// ---------------------------------------------------------------- giroconti

	public List<Transfer> transfers(UUID userId) {
		return transfers.findByUserIdOrderByTransferDateDescCreatedAtDesc(userId);
	}

	@Transactional
	public Transfer createTransfer(UUID userId, UUID fromId, UUID toId, BigDecimal amount, LocalDate date,
			String description) {
		if (fromId == null || toId == null || fromId.equals(toId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scegli due conti diversi");
		}
		if (amount == null || amount.signum() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'importo deve essere positivo");
		}
		owned(userId, fromId);
		owned(userId, toId);
		String note = description == null ? null : description.trim();
		return transfers.save(Transfer.builder().userId(userId).fromAccountId(fromId).toAccountId(toId).amount(amount)
				.transferDate(date == null ? LocalDate.now() : date)
				.description(note == null || note.isEmpty() ? null : (note.length() > 100 ? note.substring(0, 100) : note))
				.build());
	}

	@Transactional
	public void deleteTransfer(UUID userId, UUID id) {
		Transfer t = transfers.findById(id).filter(x -> userId.equals(x.getUserId()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Giroconto non trovato"));
		transfers.delete(t);
	}

	// ---------------------------------------------------------------- dettagli

	private Account owned(UUID userId, UUID id) {
		return accounts.findById(id).filter(a -> userId.equals(a.getUserId()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conto non trovato"));
	}

	private static void fill(Account a, String name, String type, BigDecimal initialBalance, String color) {
		String clean = name == null ? "" : name.trim();
		if (clean.isEmpty() || clean.length() > 40) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serve un nome (massimo 40 caratteri)");
		}
		String t = type == null ? "" : type.trim().toUpperCase();
		a.setName(clean);
		a.setType(TYPES.contains(t) ? t : "ALTRO");
		a.setInitialBalance(initialBalance == null ? BigDecimal.ZERO : initialBalance);
		a.setColor(color == null || color.isBlank() ? "#9e5545" : color.trim());
	}
}
