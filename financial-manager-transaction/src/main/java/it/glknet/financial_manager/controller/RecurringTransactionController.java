package it.glknet.financial_manager.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import it.glknet.financial_manager.config.AuthInterceptor;
import it.glknet.financial_manager.model.RecurringTransaction;
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.service.RecurringTransactionService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/recurring-transactions")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
public class RecurringTransactionController {

    private final RecurringTransactionService recurringService;

    @GetMapping
    public ResponseEntity<List<RecurringTransaction>> getAll(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
        return ResponseEntity.ok(recurringService.getAll(userId));
    }

    @PostMapping
    public ResponseEntity<RecurringTransaction> create(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
            @RequestBody RecurringTransaction item) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recurringService.save(userId, item));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecurringTransaction> update(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
            @PathVariable UUID id, @RequestBody RecurringTransaction details) {
        return ResponseEntity.ok(recurringService.update(userId, id, details));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @PathVariable UUID id) {
        recurringService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<Transaction> confirm(
            @RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        Transaction tx = recurringService.confirm(userId, id, date);
        return ResponseEntity.ok(tx);
    }

    @PostMapping("/process-auto")
    public ResponseEntity<List<Transaction>> processAuto(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
        List<Transaction> generated = recurringService.processDueAutoConfirm(userId);
        return ResponseEntity.ok(generated);
    }
}
