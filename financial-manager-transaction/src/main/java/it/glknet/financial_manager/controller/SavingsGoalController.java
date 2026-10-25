package it.glknet.financial_manager.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import it.glknet.financial_manager.config.AuthInterceptor;
import it.glknet.financial_manager.dto.AmountOperationRequest;
import it.glknet.financial_manager.model.SavingsGoal;
import it.glknet.financial_manager.model.ImportSource;
import it.glknet.financial_manager.repository.SavingsGoalRepository;
import it.glknet.financial_manager.repository.SavingsMovementRepository;
import it.glknet.financial_manager.service.CsvImportService;
import it.glknet.financial_manager.service.SavingsImportService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/savings-goals")
@RequiredArgsConstructor
public class SavingsGoalController {

    private final SavingsGoalRepository savingsGoalRepository;
    private final SavingsImportService savingsImport;
    private final SavingsMovementRepository movements;

    /** Spaces e IBAN visti negli estratti conto: le scelte per collegare un salvadanaio. */
    @GetMapping("/sources")
    public List<ImportSource> sources(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
        return savingsImport.sources(userId);
    }

    /** Versamenti e prelievi trovati nell'import (anteprima confermata dall'utente). */
    @PostMapping("/import-movements")
    public SavingsImportService.ApplyResult importMovements(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
            @RequestBody List<SavingsImportService.SavingsMove> moves) {
        return savingsImport.apply(userId, moves);
    }

    /** Collegamento a uno Space o a un IBAN; tipo sconosciuto o chiave vuota = salvadanaio a mano. */
    private static void copySource(SavingsGoal from, SavingsGoal to) {
        boolean valid = (SavingsImportService.SPACE.equals(from.getSourceType())
                || SavingsImportService.IBAN.equals(from.getSourceType()))
                && from.getSourceKey() != null && !from.getSourceKey().isBlank();
        to.setSourceType(valid ? from.getSourceType() : null);
        to.setSourceKey(valid ? (SavingsImportService.IBAN.equals(from.getSourceType())
                ? CsvImportService.normalizeIban(from.getSourceKey()) : from.getSourceKey().trim()) : null);
        to.setSourceLabel(valid ? from.getSourceLabel() : null);
    }

    private Optional<SavingsGoal> owned(UUID userId, UUID id) {
        return savingsGoalRepository.findById(id).filter(g -> userId.equals(g.getUserId()));
    }

    @GetMapping
    public ResponseEntity<List<SavingsGoal>> getSavingsGoals(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
        return ResponseEntity.ok(savingsGoalRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    @PostMapping
    public ResponseEntity<SavingsGoal> createSavingsGoal(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
            @RequestBody SavingsGoal goal) {
        goal.setId(null); // evita di sovrascrivere obiettivi esistenti
        goal.setUserId(userId);
        copySource(goal, goal);
        if (goal.getCurrentAmount() == null) {
            goal.setCurrentAmount(BigDecimal.ZERO);
        }
        if (goal.getTargetAmount() != null && goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0) {
            goal.setCompleted(true);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(savingsGoalRepository.save(goal));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SavingsGoal> updateSavingsGoal(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
            @PathVariable UUID id, @RequestBody SavingsGoal details) {
        return owned(userId, id).map(goal -> {
            goal.setName(details.getName());
            goal.setDescription(details.getDescription());
            goal.setTargetAmount(details.getTargetAmount());
            if (details.getCurrentAmount() != null) {
                goal.setCurrentAmount(details.getCurrentAmount());
            }
            goal.setTargetDate(details.getTargetDate());
            goal.setColor(details.getColor());
            goal.setIcon(details.getIcon());
            copySource(details, goal);
            if (goal.getTargetAmount() != null) {
                goal.setCompleted(goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0);
            }
            return ResponseEntity.ok(savingsGoalRepository.save(goal));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteSavingsGoal(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
            @PathVariable UUID id) {
        return owned(userId, id).map(goal -> {
            movements.deleteByGoalId(goal.getId());
            savingsGoalRepository.delete(goal);
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<SavingsGoal> deposit(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
            @PathVariable UUID id, @RequestBody AmountOperationRequest req) {
        BigDecimal depositAmt = positive(req);
        return owned(userId, id).map(goal -> {
            BigDecimal newAmount = goal.getCurrentAmount().add(depositAmt);
            goal.setCurrentAmount(newAmount);
            if (goal.getTargetAmount() != null && newAmount.compareTo(goal.getTargetAmount()) >= 0) {
                goal.setCompleted(true);
            }
            return ResponseEntity.ok(savingsGoalRepository.save(goal));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<SavingsGoal> withdraw(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
            @PathVariable UUID id, @RequestBody AmountOperationRequest req) {
        BigDecimal withdrawAmt = positive(req);
        return owned(userId, id).map(goal -> {
            BigDecimal newAmount = goal.getCurrentAmount().subtract(withdrawAmt);
            if (newAmount.signum() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Non puoi prelevare piu' di quanto c'e' nel salvadanaio (" + goal.getCurrentAmount() + " €)");
            }
            goal.setCurrentAmount(newAmount);
            if (goal.getTargetAmount() != null && newAmount.compareTo(goal.getTargetAmount()) < 0) {
                goal.setCompleted(false);
            }
            return ResponseEntity.ok(savingsGoalRepository.save(goal));
        }).orElse(ResponseEntity.notFound().build());
    }

    private static BigDecimal positive(AmountOperationRequest req) {
        if (req.getAmount() == null || req.getAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'importo deve essere maggiore di zero");
        }
        return req.getAmount();
    }
}
