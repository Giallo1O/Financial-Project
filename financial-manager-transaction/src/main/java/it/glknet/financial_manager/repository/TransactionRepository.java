package it.glknet.financial_manager.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.glknet.financial_manager.model.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByUserId(UUID userId);

    List<Transaction> findByUserIdAndTransactionDateBetween(UUID userId, LocalDate from, LocalDate to);

    @Modifying
    @Query("UPDATE Transaction t SET t.accountId = null WHERE t.accountId = :accountId")
    void detachAccount(@Param("accountId") UUID accountId);

    /** Assegna a un conto tutti i movimenti dell'utente che non ne hanno uno; restituisce quanti. */
    @Modifying
    @Query("UPDATE Transaction t SET t.accountId = :accountId WHERE t.userId = :userId AND t.accountId IS NULL")
    int assignUnassigned(@Param("userId") UUID userId, @Param("accountId") UUID accountId);

    /** Totali per conto (accountId null = senza conto) e tipo, fino a una data. */
    @Query("SELECT t.accountId, t.type, SUM(t.amount) FROM Transaction t WHERE t.userId = :userId "
            + "AND t.transactionDate <= :until GROUP BY t.accountId, t.type")
    List<Object[]> sumsByAccount(@Param("userId") UUID userId, @Param("until") LocalDate until);

    @Modifying
    @Query("UPDATE Transaction t SET t.categoryId = null WHERE t.categoryId = :categoryId")
    void detachCategory(@Param("categoryId") UUID categoryId);

    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId " +
           "AND EXTRACT(MONTH FROM t.transactionDate) = :month " +
           "AND EXTRACT(YEAR FROM t.transactionDate) = :year")
    List<Transaction> findByUserIdAndMonthAndYear(
            @Param("userId") UUID userId,
            @Param("month") int month,
            @Param("year") int year);
}
