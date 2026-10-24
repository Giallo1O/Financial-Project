package it.glknet.financial_manager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.glknet.financial_manager.model.SharedExpense;

public interface SharedExpenseRepository extends JpaRepository<SharedExpense, UUID> {

	List<SharedExpense> findByGroupIdOrderByExpenseDateDescCreatedAtDesc(UUID groupId);

	void deleteByGroupId(UUID groupId);
}
