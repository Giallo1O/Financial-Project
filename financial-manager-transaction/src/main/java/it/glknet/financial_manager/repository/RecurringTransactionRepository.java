package it.glknet.financial_manager.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.glknet.financial_manager.model.RecurringTransaction;

@Repository
public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, UUID> {
    List<RecurringTransaction> findByUserIdOrderByDayOfMonthAsc(UUID userId);

    @Modifying
    @Query("UPDATE RecurringTransaction r SET r.accountId = null WHERE r.accountId = :accountId")
    void detachAccount(@Param("accountId") UUID accountId);

    @Modifying
    @Query("UPDATE RecurringTransaction r SET r.categoryId = null WHERE r.categoryId = :categoryId")
    void detachCategory(@Param("categoryId") UUID categoryId);
    List<RecurringTransaction> findByUserIdAndActiveTrueOrderByDayOfMonthAsc(UUID userId);
    List<RecurringTransaction> findByActiveTrueAndAutoConfirmTrueAndNextDueDateLessThanEqual(LocalDate date);
    List<RecurringTransaction> findByUserIdAndActiveTrueAndAutoConfirmTrueAndNextDueDateLessThanEqual(UUID userId, LocalDate date);
}
