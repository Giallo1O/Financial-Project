package it.glknet.financial_manager.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.glknet.financial_manager.model.Category;
import it.glknet.financial_manager.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "http://localhost:3000") // Frontend Docker port
@RequiredArgsConstructor
public class CategoryController {

	private final CategoryRepository categoryRepository;

	// 1. Ottieni tutte le categorie di un utente
	@GetMapping
	public ResponseEntity<List<Category>> getUserCategories(@RequestParam String userId) {
		return ResponseEntity.ok(categoryRepository.findByUserId(userId));
	}

	// 2. Crea una nuova categoria
	@PostMapping
	public ResponseEntity<Category> createCategory(@RequestBody Category category) {
		Category savedCategory = categoryRepository.save(category);
		return ResponseEntity.status(HttpStatus.CREATED).body(savedCategory);
	}

}
