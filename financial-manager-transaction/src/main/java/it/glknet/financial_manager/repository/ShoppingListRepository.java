package it.glknet.financial_manager.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.glknet.financial_manager.model.ShoppingList;

public interface ShoppingListRepository extends JpaRepository<ShoppingList, UUID> {

	List<ShoppingList> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

	Optional<ShoppingList> findByInviteCode(String inviteCode);
}
