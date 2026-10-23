package it.glknet.financial_manager.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import it.glknet.financial_manager.model.CategoryRule;
import it.glknet.financial_manager.service.CategoryRuleService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/category-rules")
@RequiredArgsConstructor
public class CategoryRuleController {

	/** keyword: parola (o parole) che deve comparire nella descrizione. */
	public record RuleRequest(String keyword, UUID categoryId) {
	}

	private final CategoryRuleService service;

	@GetMapping
	public List<CategoryRule> list(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
		return service.list(userId);
	}

	/** Crea la regola, o ne cambia la categoria se la parola chiave esiste gia'. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CategoryRule save(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @RequestBody RuleRequest request) {
		return service.save(userId, request.keyword(), request.categoryId());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @PathVariable UUID id) {
		service.delete(userId, id);
		return ResponseEntity.noContent().build();
	}

	/** Crea regole dalle transazioni gia' categorizzate. */
	@PostMapping("/learn-from-history")
	public Map<String, Integer> learnFromHistory(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
		return Map.of("created", service.learnFromHistory(userId));
	}
}
