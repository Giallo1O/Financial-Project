package it.glknet.financial_manager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.glknet.financial_manager.model.SavingsMovement;

public interface SavingsMovementRepository extends JpaRepository<SavingsMovement, UUID> {

	List<SavingsMovement> findByUserId(UUID userId);

	@Modifying
	@Query("DELETE FROM SavingsMovement m WHERE m.goalId = :goalId")
	void deleteByGoalId(@Param("goalId") UUID goalId);
}
