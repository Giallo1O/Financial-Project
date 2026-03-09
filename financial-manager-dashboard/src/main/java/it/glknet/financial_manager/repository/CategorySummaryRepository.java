package it.glknet.financial_manager.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.glknet.financial_manager.model.CategorySummary;

public interface CategorySummaryRepository extends JpaRepository<CategorySummary, UUID> {

	// Per il Consumer: trova la riga esatta da aggiornare
	Optional<CategorySummary> findByUserIdAndCategoryId(String userId, UUID categoryId);

	// Per il Controller (API Frontend): trova tutte le statistiche di un utente
	List<CategorySummary> findByUserId(String userId);
}
