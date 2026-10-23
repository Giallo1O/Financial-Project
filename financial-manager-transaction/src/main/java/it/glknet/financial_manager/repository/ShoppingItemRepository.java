package it.glknet.financial_manager.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.glknet.financial_manager.model.ShoppingItem;

public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, UUID> {

	List<ShoppingItem> findByListId(UUID listId);

	void deleteByListId(UUID listId);

	void deleteByListIdAndCheckedTrue(UUID listId);
}
