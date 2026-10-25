package it.glknet.financial_manager.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.glknet.financial_manager.model.SharedGroup;

public interface SharedGroupRepository extends JpaRepository<SharedGroup, UUID> {

	Optional<SharedGroup> findByInviteCode(String inviteCode);
}
