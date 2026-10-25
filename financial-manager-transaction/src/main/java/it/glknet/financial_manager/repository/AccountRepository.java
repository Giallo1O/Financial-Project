package it.glknet.financial_manager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.glknet.financial_manager.model.Account;

public interface AccountRepository extends JpaRepository<Account, UUID> {

	List<Account> findByUserIdOrderByCreatedAtAsc(UUID userId);
}
