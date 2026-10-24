package it.glknet.financial_manager.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.glknet.financial_manager.model.HomeAssistantLink;

public interface HomeAssistantLinkRepository extends JpaRepository<HomeAssistantLink, UUID> {
}
