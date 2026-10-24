package it.glknet.financial_manager.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import it.glknet.financial_manager.dto.TransactionRequest;
import it.glknet.financial_manager.model.RecurringTransaction;
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.repository.RecurringTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringTransactionService {

    private final RecurringTransactionRepository recurringRepository;
    private final TransactionService transactionService;

    public List<RecurringTransaction> getAll(UUID userId) {
        return recurringRepository.findByUserIdOrderByDayOfMonthAsc(userId);
    }

    private RecurringTransaction findOwned(UUID userId, UUID id) {
        return recurringRepository.findById(id).filter(r -> userId.equals(r.getUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring transaction not found"));
    }

    public RecurringTransaction save(UUID userId, RecurringTransaction item) {
        item.setId(null); // evita di sovrascrivere ricorrenze esistenti
        item.setUserId(userId);
        if (item.getDayOfMonth() == null || item.getDayOfMonth() < 1 || item.getDayOfMonth() > 31) {
            item.setDayOfMonth(1);
        }
        if (item.getNextDueDate() == null) {
            LocalDate now = LocalDate.now();
            int day = Math.min(item.getDayOfMonth(), now.lengthOfMonth());
            LocalDate due = now.withDayOfMonth(day);
            if (due.isBefore(now)) {
                LocalDate nextMonth = now.plusMonths(1);
                due = nextMonth.withDayOfMonth(Math.min(item.getDayOfMonth(), nextMonth.lengthOfMonth()));
            }
            item.setNextDueDate(due);
        }
        return recurringRepository.save(item);
    }

    public RecurringTransaction update(UUID userId, UUID id, RecurringTransaction details) {
        return java.util.Optional.of(findOwned(userId, id)).map(r -> {
            r.setDescription(details.getDescription());
            r.setAmount(details.getAmount());
            r.setType(details.getType());
            r.setCategoryId(details.getCategoryId());
            r.setAccountId(details.getAccountId());
            r.setRecurrenceInterval(details.getRecurrenceInterval());
            r.setDayOfMonth(details.getDayOfMonth());
            r.setStartDate(details.getStartDate());
            r.setEndDate(details.getEndDate());
            r.setAutoConfirm(details.isAutoConfirm());
            r.setActive(details.isActive());
            if (details.getNextDueDate() != null) {
                r.setNextDueDate(details.getNextDueDate());
            }
            return recurringRepository.save(r);
        }).orElseThrow(() -> new IllegalArgumentException("Recurring transaction not found"));
    }

    public void delete(UUID userId, UUID id) {
        recurringRepository.delete(findOwned(userId, id));
    }

    @Transactional
    public Transaction confirm(UUID userId, UUID id, LocalDate confirmationDate) {
        return generate(findOwned(userId, id), confirmationDate);
    }

    private Transaction generate(RecurringTransaction recurring, LocalDate confirmationDate) {
        LocalDate effectiveDate = confirmationDate != null ? confirmationDate : (recurring.getNextDueDate() != null ? recurring.getNextDueDate() : LocalDate.now());

        TransactionRequest txRequest = new TransactionRequest(
                recurring.getDescription(),
                recurring.getAmount(),
                recurring.getType(),
                recurring.getCategoryId(),
                recurring.getUserId(),
                effectiveDate,
                recurring.getAccountId()
        );
        Transaction createdTx = transactionService.createTransaction(txRequest);

        LocalDate baseDate = recurring.getNextDueDate() != null ? recurring.getNextDueDate() : effectiveDate;
        LocalDate nextMonth = baseDate.plusMonths(1);
        int day = (recurring.getDayOfMonth() != null && recurring.getDayOfMonth() >= 1 && recurring.getDayOfMonth() <= 31)
                ? recurring.getDayOfMonth()
                : baseDate.getDayOfMonth();
        int validDay = Math.min(day, nextMonth.lengthOfMonth());
        recurring.setNextDueDate(nextMonth.withDayOfMonth(validDay));
        recurring.setLastGeneratedDate(effectiveDate);
        if (pastEnd(recurring)) {
            recurring.setActive(false); // l'ultima scadenza e' stata generata: la ricorrenza e' finita
        }

        recurringRepository.save(recurring);
        return createdTx;
    }

    private static boolean pastEnd(RecurringTransaction r) {
        return r.getEndDate() != null && r.getNextDueDate() != null && r.getNextDueDate().isAfter(r.getEndDate());
    }

    @Transactional
    public List<Transaction> processDueAutoConfirm(UUID userId) {
        LocalDate today = LocalDate.now();
        List<RecurringTransaction> dueList = (userId != null)
                ? recurringRepository.findByUserIdAndActiveTrueAndAutoConfirmTrueAndNextDueDateLessThanEqual(userId, today)
                : recurringRepository.findByActiveTrueAndAutoConfirmTrueAndNextDueDateLessThanEqual(today);

        List<Transaction> generated = new ArrayList<>();
        for (RecurringTransaction r : dueList) {
            if (pastEnd(r)) {
                r.setActive(false);
                recurringRepository.save(r);
                continue;
            }
            try {
                Transaction tx = generate(r, r.getNextDueDate());
                generated.add(tx);
                log.info("Auto-confirmed recurring transaction {} ({}) for user {}", r.getId(), r.getDescription(), r.getUserId());
            } catch (Exception e) {
                log.error("Failed to auto-confirm recurring transaction {}: {}", r.getId(), e.getMessage());
            }
        }
        return generated;
    }

    @Scheduled(cron = "0 0 1 * * ?")
    public void scheduledAutoConfirm() {
        log.info("Starting scheduled auto-confirmation of recurring transactions");
        processDueAutoConfirm(null);
    }
}
