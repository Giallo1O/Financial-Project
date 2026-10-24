package it.glknet.financial_manager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.glknet.financial_manager.model.SharedSettlement;

public interface SharedSettlementRepository extends JpaRepository<SharedSettlement, UUID> {

	List<SharedSettlement> findByGroupIdOrderBySettleDateDescCreatedAtDesc(UUID groupId);

	void deleteByGroupId(UUID groupId);
}
