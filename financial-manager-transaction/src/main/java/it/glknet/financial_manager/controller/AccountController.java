package it.glknet.financial_manager.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import it.glknet.financial_manager.config.AuthInterceptor;
import it.glknet.financial_manager.dto.AccountsOverview;
import it.glknet.financial_manager.model.Account;
import it.glknet.financial_manager.model.Transfer;
import it.glknet.financial_manager.service.AccountService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccountController {

	public record AccountRequest(String name, String type, BigDecimal initialBalance, String color, Boolean archived) {
	}

	public record AssignRequest(UUID accountId) {
	}

	public record TransferRequest(UUID fromAccountId, UUID toAccountId, BigDecimal amount, LocalDate date,
			String description) {
	}

	private final AccountService service;

	@GetMapping("/accounts")
	public AccountsOverview overview(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
		return service.overview(userId);
	}

	@PostMapping("/accounts")
	@ResponseStatus(HttpStatus.CREATED)
	public Account create(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @RequestBody AccountRequest r) {
		return service.create(userId, r.name(), r.type(), r.initialBalance(), r.color());
	}

	@PutMapping("/accounts/{id}")
	public Account update(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @PathVariable UUID id,
			@RequestBody AccountRequest r) {
		return service.update(userId, id, r.name(), r.type(), r.initialBalance(), r.color(), r.archived());
	}

	@DeleteMapping("/accounts/{id}")
	public ResponseEntity<Void> delete(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @PathVariable UUID id) {
		service.delete(userId, id);
		return ResponseEntity.noContent().build();
	}

	/** Mette su un conto tutti i movimenti che non ne hanno uno. */
	@PostMapping("/accounts/assign-unassigned")
	public Map<String, Integer> assignUnassigned(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestBody AssignRequest r) {
		return Map.of("assigned", service.assignUnassigned(userId, r.accountId()));
	}

	@GetMapping("/transfers")
	public List<Transfer> transfers(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
		return service.transfers(userId);
	}

	@PostMapping("/transfers")
	@ResponseStatus(HttpStatus.CREATED)
	public Transfer createTransfer(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestBody TransferRequest r) {
		return service.createTransfer(userId, r.fromAccountId(), r.toAccountId(), r.amount(), r.date(),
				r.description());
	}

	@DeleteMapping("/transfers/{id}")
	public ResponseEntity<Void> deleteTransfer(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@PathVariable UUID id) {
		service.deleteTransfer(userId, id);
		return ResponseEntity.noContent().build();
	}
}
