package it.glknet.financial_manager.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
import it.glknet.financial_manager.model.Category;
import it.glknet.financial_manager.repository.CategoryRepository;
import it.glknet.financial_manager.repository.CategoryRuleRepository;
import it.glknet.financial_manager.repository.RecurringTransactionRepository;
import it.glknet.financial_manager.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

	private final CategoryRepository categoryRepository;
	private final TransactionRepository transactionRepository;
	private final RecurringTransactionRepository recurringRepository;
	private final CategoryRuleRepository ruleRepository;

	// 1. Ottieni tutte le categorie di un utente
	@GetMapping
	public ResponseEntity<List<Category>> getUserCategories(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
		return ResponseEntity.ok(categoryRepository.findByUserId(userId));
	}

	// 2. Crea una nuova categoria
	@PostMapping
	public ResponseEntity<Category> createCategory(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestBody Category category) {
		category.setId(null); // evita di sovrascrivere categorie esistenti
		category.setUserId(userId);
		Category savedCategory = categoryRepository.save(category);
		return ResponseEntity.status(HttpStatus.CREATED).body(savedCategory);
	}

	// 3. Modifica una categoria esistente
	@PutMapping("/{id}")
	public ResponseEntity<Category> updateCategory(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@PathVariable UUID id, @RequestBody Category categoryDetails) {
		return categoryRepository.findById(id).filter(c -> userId.equals(c.getUserId())).map(category -> {
			category.setName(categoryDetails.getName());
			category.setColor(categoryDetails.getColor());
			category.setIcon(categoryDetails.getIcon());
			category.setExpense(categoryDetails.isExpense());
			category.setBudget(categoryDetails.getBudget());
			category.setFixed(categoryDetails.isFixed());
			category.getTags().clear();
			if (categoryDetails.getTags() != null) {
				category.getTags().addAll(categoryDetails.getTags());
			}
			Category updatedCategory = categoryRepository.save(category);
			return ResponseEntity.ok(updatedCategory);
		}).orElse(ResponseEntity.notFound().build());
	}

	// 4. Elimina una categoria
	@DeleteMapping("/{id}")
	@Transactional
	public ResponseEntity<Void> deleteCategory(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@PathVariable UUID id) {
		categoryRepository.findById(id).filter(c -> userId.equals(c.getUserId())).ifPresent(category -> {
			// Le transazioni restano (i totali non cambiano) ma perdono la categoria
			transactionRepository.detachCategory(id);
			recurringRepository.detachCategory(id);
			ruleRepository.deleteByCategoryId(id);
			categoryRepository.delete(category);
		});
		return ResponseEntity.noContent().build();
	}

}
