package it.glknet.financial_manager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.glknet.financial_manager.model.ImportSource;

public interface ImportSourceRepository extends JpaRepository<ImportSource, UUID> {

	List<ImportSource> findByUserId(UUID userId);
}
