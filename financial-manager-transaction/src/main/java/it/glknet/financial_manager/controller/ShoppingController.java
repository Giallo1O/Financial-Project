package it.glknet.financial_manager.controller;

import java.math.BigDecimal;
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
import it.glknet.financial_manager.dto.ShoppingItemView;
import it.glknet.financial_manager.dto.ShoppingListView;
import it.glknet.financial_manager.dto.ShoppingSuggestion;
import it.glknet.financial_manager.service.ShoppingService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/shopping-lists")
@RequiredArgsConstructor
public class ShoppingController {

	public record NewList(String name) {
	}

	public record JoinRequest(String code) {
	}

	public record NewItem(String name, BigDecimal quantity, String unit) {
	}

	/** Campi non indicati (null) restano invariati. */
	public record ItemUpdate(Boolean checked, BigDecimal quantity) {
	}

	private final ShoppingService service;

	@GetMapping
	public List<ShoppingListView> lists(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
		return service.lists(userId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ShoppingListView create(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestAttribute(AuthInterceptor.USERNAME) String username, @RequestBody NewList request) {
		return service.create(userId, username, request.name());
	}

	/** Il proprietario elimina la lista, gli altri ne escono. */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteOrLeave(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@PathVariable UUID id) {
		service.deleteOrLeave(userId, id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/invite")
	public Map<String, String> invite(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @PathVariable UUID id) {
		return Map.of("code", service.createInvite(userId, id));
	}

	@DeleteMapping("/{id}/invite")
	public ResponseEntity<Void> revokeInvite(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@PathVariable UUID id) {
		service.revokeInvite(userId, id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/join")
	public ShoppingListView join(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestAttribute(AuthInterceptor.USERNAME) String username, @RequestBody JoinRequest request) {
		return service.join(userId, username, request.code());
	}

	@GetMapping("/{id}/items")
	public List<ShoppingItemView> items(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @PathVariable UUID id) {
		return service.items(userId, id);
	}

	@PostMapping("/{id}/items")
	@ResponseStatus(HttpStatus.CREATED)
	public ShoppingItemView addItem(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestAttribute(AuthInterceptor.USERNAME) String username, @PathVariable UUID id,
			@RequestBody NewItem request) {
		return service.addItem(userId, username, id, request.name(), request.quantity(), request.unit());
	}

	@PutMapping("/{id}/items/{itemId}")
	public ShoppingItemView update(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @PathVariable UUID id,
			@PathVariable UUID itemId, @RequestBody ItemUpdate request) {
		return service.update(userId, id, itemId, request.checked(), request.quantity());
	}

	@DeleteMapping("/{id}/items/{itemId}")
	public ResponseEntity<Void> deleteItem(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@PathVariable UUID id, @PathVariable UUID itemId) {
		service.deleteItem(userId, id, itemId);
		return ResponseEntity.noContent().build();
	}

	/** Toglie dalla lista le voci gia' prese. */
	@DeleteMapping("/{id}/items")
	public ResponseEntity<Void> clearChecked(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@PathVariable UUID id) {
		service.clearChecked(userId, id);
		return ResponseEntity.noContent().build();
	}

	/** Prodotti che compri di solito, dai tuoi scontrini (non dipende da una lista). */
	@GetMapping("/suggestions")
	public List<ShoppingSuggestion> suggestions(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
		return service.suggestions(userId);
	}
}
