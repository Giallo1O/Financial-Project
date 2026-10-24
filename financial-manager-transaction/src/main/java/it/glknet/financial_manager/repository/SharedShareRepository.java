package it.glknet.financial_manager.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.glknet.financial_manager.model.SharedShare;

public interface SharedShareRepository extends JpaRepository<SharedShare, UUID> {

	List<SharedShare> findByExpenseIdIn(Collection<UUID> expenseIds);

	void deleteByExpenseIdIn(Collection<UUID> expenseIds);
}
