package it.glknet.financial_manager.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.glknet.financial_manager.model.CategorySummary;

public interface CategorySummaryRepository extends JpaRepository<CategorySummary, UUID> {

	// Per il Consumer: trova la riga esatta da aggiornare
	Optional<CategorySummary> findByUserIdAndCategoryIdAndMonthAndYear(UUID userId, UUID categoryId, Integer month,
			Integer year);

	// Per il Controller (API Frontend): trova tutte le statistiche di un utente per
	// un dato mese
	List<CategorySummary> findByUserIdAndMonthAndYear(UUID userId, Integer month, Integer year);

	Optional<CategorySummary> findByUserIdAndCategoryId(UUID userId, UUID categoryId);
}
