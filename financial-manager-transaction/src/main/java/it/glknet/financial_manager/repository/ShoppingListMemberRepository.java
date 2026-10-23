package it.glknet.financial_manager.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.glknet.financial_manager.model.ShoppingListMember;

public interface ShoppingListMemberRepository extends JpaRepository<ShoppingListMember, UUID> {

	List<ShoppingListMember> findByUserId(UUID userId);

	List<ShoppingListMember> findByListId(UUID listId);

	boolean existsByListIdAndUserId(UUID listId, UUID userId);

	void deleteByListIdAndUserId(UUID listId, UUID userId);

	void deleteByListId(UUID listId);
}
