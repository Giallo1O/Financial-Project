package it.glknet.financial_manager.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.glknet.financial_manager.dto.ProductRow;
import it.glknet.financial_manager.model.ReceiptItem;

public interface ReceiptItemRepository extends JpaRepository<ReceiptItem, UUID> {

	List<ReceiptItem> findByReceiptId(UUID receiptId);

	@Modifying
	@Query("DELETE FROM ReceiptItem i WHERE i.receiptId = :receiptId")
	void deleteByReceiptId(@Param("receiptId") UUID receiptId);

	@Query("SELECT new it.glknet.financial_manager.dto.ProductRow(i.name, i.unit, i.foodCategory, i.quantity, "
			+ "i.unitPrice, i.totalPrice, r.purchaseDate, r.storeName) "
			+ "FROM ReceiptItem i, Receipt r WHERE i.receiptId = r.id AND r.userId = :userId "
			+ "AND r.purchaseDate BETWEEN :from AND :to")
	List<ProductRow> findRows(@Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
