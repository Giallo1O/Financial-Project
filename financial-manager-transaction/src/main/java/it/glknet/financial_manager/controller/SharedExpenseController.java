package it.glknet.financial_manager.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import it.glknet.financial_manager.config.AuthInterceptor;
import it.glknet.financial_manager.dto.GroupViews.ExpenseView;
import it.glknet.financial_manager.dto.GroupViews.GroupView;
import it.glknet.financial_manager.dto.GroupViews.SettlementView;
import it.glknet.financial_manager.dto.GroupViews.Summary;
import it.glknet.financial_manager.service.SharedExpenseService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class SharedExpenseController {

	public record NewGroup(String name) {
	}

	public record JoinRequest(String code) {
	}

	public record NewExpense(String description, BigDecimal amount, UUID paidBy, LocalDate date, List<UUID> participants) {
	}

	public record NewSettlement(UUID from, UUID to, BigDecimal amount, LocalDate date) {
	}

	private final SharedExpenseService service;

	@GetMapping
	public List<GroupView> groups(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
		return service.groups(userId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public GroupView create(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestAttribute(AuthInterceptor.USERNAME) String username, @RequestBody NewGroup request) {
		return service.create(userId, username, request.name());
	}

	@PostMapping("/join")
	public GroupView join(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestAttribute(AuthInterceptor.USERNAME) String username, @RequestBody JoinRequest request) {
		return service.join(userId, username, request.code());
	}

	@PostMapping("/{id}/invite")
	public Map<String, String> invite(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @PathVariable UUID id) {
		return Map.of("code", service.createInvite(userId, id));
	}

	@DeleteMapping("/{id}/invite")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void revokeInvite(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @PathVariable UUID id) {
		service.revokeInvite(userId, id);
	}

	/** Esce dal gruppo (solo a conti pareggiati). */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void leave(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @PathVariable UUID id) {
		service.leave(userId, id);
	}

	@GetMapping("/{id}/summary")
	public Summary summary(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @PathVariable UUID id) {
		return service.summary(userId, id);
	}

	@PostMapping("/{id}/expenses")
	@ResponseStatus(HttpStatus.CREATED)
	public ExpenseView addExpense(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @PathVariable UUID id,
			@RequestBody NewExpense r) {
		return service.addExpense(userId, id, r.description(), r.amount(), r.paidBy(), r.date(), r.participants());
	}

	@DeleteMapping("/{id}/expenses/{expenseId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteExpense(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @PathVariable UUID id,
			@PathVariable UUID expenseId) {
		service.deleteExpense(userId, id, expenseId);
	}

	@PostMapping("/{id}/settlements")
	@ResponseStatus(HttpStatus.CREATED)
	public SettlementView settle(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @PathVariable UUID id,
			@RequestBody NewSettlement r) {
		return service.settle(userId, id, r.from(), r.to(), r.amount(), r.date());
	}

	@DeleteMapping("/{id}/settlements/{settlementId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteSettlement(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @PathVariable UUID id,
			@PathVariable UUID settlementId) {
		service.deleteSettlement(userId, id, settlementId);
	}
}
