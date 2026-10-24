package it.glknet.financial_manager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.glknet.financial_manager.model.Transfer;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

	List<Transfer> findByUserIdOrderByTransferDateDescCreatedAtDesc(UUID userId);

	boolean existsByFromAccountIdOrToAccountId(UUID fromAccountId, UUID toAccountId);
}
