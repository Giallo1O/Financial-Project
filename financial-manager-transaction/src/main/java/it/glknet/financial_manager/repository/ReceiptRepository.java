package it.glknet.financial_manager.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.glknet.financial_manager.model.Receipt;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

	List<Receipt> findByUserIdOrderByPurchaseDateDescCreatedAtDesc(UUID userId);

	List<Receipt> findByUserIdAndPurchaseDateBetween(UUID userId, LocalDate from, LocalDate to);

	List<Receipt> findByTransactionIdIn(Collection<UUID> transactionIds);

	boolean existsByUserIdAndPurchaseDateAndTotalAndStoreNameIgnoreCase(UUID userId, LocalDate purchaseDate,
			BigDecimal total, String storeName);
}
