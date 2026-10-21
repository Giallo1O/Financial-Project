package it.glknet.financial_manager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.glknet.financial_manager.model.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByUserId(UUID userId);

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
