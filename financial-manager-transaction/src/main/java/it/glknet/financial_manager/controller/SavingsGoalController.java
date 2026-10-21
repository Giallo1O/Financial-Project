package it.glknet.financial_manager.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.glknet.financial_manager.dto.AmountOperationRequest;
import it.glknet.financial_manager.model.SavingsGoal;
import it.glknet.financial_manager.repository.SavingsGoalRepository;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/savings-goals")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
public class SavingsGoalController {

    private final SavingsGoalRepository savingsGoalRepository;

    @GetMapping
    public ResponseEntity<List<SavingsGoal>> getSavingsGoals(@RequestParam UUID userId) {
        return ResponseEntity.ok(savingsGoalRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    @PostMapping
    public ResponseEntity<SavingsGoal> createSavingsGoal(@RequestBody SavingsGoal goal) {
        if (goal.getCurrentAmount() == null) {
            goal.setCurrentAmount(BigDecimal.ZERO);
        }
        if (goal.getTargetAmount() != null && goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0) {
            goal.setCompleted(true);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(savingsGoalRepository.save(goal));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SavingsGoal> updateSavingsGoal(@PathVariable UUID id, @RequestBody SavingsGoal details) {
        return savingsGoalRepository.findById(id).map(goal -> {
            goal.setName(details.getName());
            goal.setDescription(details.getDescription());
            goal.setTargetAmount(details.getTargetAmount());
            if (details.getCurrentAmount() != null) {
                goal.setCurrentAmount(details.getCurrentAmount());
            }
            goal.setTargetDate(details.getTargetDate());
            goal.setColor(details.getColor());
            goal.setIcon(details.getIcon());
            if (goal.getTargetAmount() != null) {
                goal.setCompleted(goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0);
            }
            return ResponseEntity.ok(savingsGoalRepository.save(goal));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSavingsGoal(@PathVariable UUID id) {
        if (savingsGoalRepository.existsById(id)) {
            savingsGoalRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<SavingsGoal> deposit(@PathVariable UUID id, @RequestBody AmountOperationRequest req) {
        return savingsGoalRepository.findById(id).map(goal -> {
            BigDecimal depositAmt = (req.getAmount() != null && req.getAmount().compareTo(BigDecimal.ZERO) > 0)
                    ? req.getAmount()
                    : BigDecimal.ZERO;
            BigDecimal newAmount = goal.getCurrentAmount().add(depositAmt);
            goal.setCurrentAmount(newAmount);
            if (goal.getTargetAmount() != null && newAmount.compareTo(goal.getTargetAmount()) >= 0) {
                goal.setCompleted(true);
            }
            return ResponseEntity.ok(savingsGoalRepository.save(goal));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<SavingsGoal> withdraw(@PathVariable UUID id, @RequestBody AmountOperationRequest req) {
        return savingsGoalRepository.findById(id).map(goal -> {
            BigDecimal withdrawAmt = (req.getAmount() != null && req.getAmount().compareTo(BigDecimal.ZERO) > 0)
                    ? req.getAmount()
                    : BigDecimal.ZERO;
            BigDecimal newAmount = goal.getCurrentAmount().subtract(withdrawAmt);
            if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
                newAmount = BigDecimal.ZERO;
            }
            goal.setCurrentAmount(newAmount);
            if (goal.getTargetAmount() != null && newAmount.compareTo(goal.getTargetAmount()) < 0) {
                goal.setCompleted(false);
            }
            return ResponseEntity.ok(savingsGoalRepository.save(goal));
        }).orElse(ResponseEntity.notFound().build());
    }
}
