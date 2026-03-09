package it.glknet.financial_manager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.glknet.financial_manager.model.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
	List<Category> findByUserId(String userId);
}
