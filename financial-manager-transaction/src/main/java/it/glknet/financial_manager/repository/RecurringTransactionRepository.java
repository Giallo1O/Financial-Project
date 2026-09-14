package it.glknet.financial_manager.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.glknet.financial_manager.model.RecurringTransaction;

@Repository
public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, UUID> {
    List<RecurringTransaction> findByUserIdOrderByDayOfMonthAsc(UUID userId);
    List<RecurringTransaction> findByUserIdAndActiveTrueOrderByDayOfMonthAsc(UUID userId);
    List<RecurringTransaction> findByActiveTrueAndAutoConfirmTrueAndNextDueDateLessThanEqual(LocalDate date);
    List<RecurringTransaction> findByUserIdAndActiveTrueAndAutoConfirmTrueAndNextDueDateLessThanEqual(UUID userId, LocalDate date);
}
