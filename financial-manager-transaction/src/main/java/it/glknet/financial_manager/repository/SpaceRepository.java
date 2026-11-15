package it.glknet.financial_manager.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.glknet.financial_manager.model.Space;

@Repository
public interface SpaceRepository extends JpaRepository<Space, UUID> {

    List<Space> findByUserIdOrderByCurrentBalanceDesc(UUID userId);

    Optional<Space> findByUserIdAndNameIgnoreCase(UUID userId, String name);

    void deleteByUserId(UUID userId);
}
