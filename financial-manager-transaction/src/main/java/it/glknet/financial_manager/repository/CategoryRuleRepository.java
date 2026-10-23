package it.glknet.financial_manager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.glknet.financial_manager.model.CategoryRule;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, UUID> {

	List<CategoryRule> findByUserId(UUID userId);

	@Modifying
	@Query("DELETE FROM CategoryRule r WHERE r.categoryId = :categoryId")
	void deleteByCategoryId(@Param("categoryId") UUID categoryId);
}
