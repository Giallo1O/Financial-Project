package it.glknet.financial_manager.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.glknet.financial_manager.model.SpaceMovement;

@Repository
public interface SpaceMovementRepository extends JpaRepository<SpaceMovement, UUID> {

    List<SpaceMovement> findBySpaceIdOrderByDateAscCreatedAtAsc(UUID spaceId);

    List<SpaceMovement> findBySpaceIdOrderByDateDescCreatedAtDesc(UUID spaceId);

    List<SpaceMovement> findByUserIdOrderByDateDesc(UUID userId);

    List<SpaceMovement> findByUserIdAndDateBetween(UUID userId, LocalDate from, LocalDate to);

    void deleteBySpaceId(UUID spaceId);

    void deleteByUserId(UUID userId);
}
