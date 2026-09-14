package it.glknet.financial_manager.model;

import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name; // es. "Spesa", "Bollette"

	private String color; // es. "#FF0000" per il frontend

	private String icon; // es. "home-outline" (opzionale per le icone UI)

	@Column(name = "user_id", nullable = false)
	private UUID userId; // Fondamentale: ogni utente vede solo le SUE categorie

	@Column(name = "is_expense", nullable = false, columnDefinition = "boolean default true")
	@Builder.Default
	private boolean expense = true;

	@Builder.Default
	private java.math.BigDecimal budget = java.math.BigDecimal.ZERO;

	@Column(name = "is_fixed", nullable = false, columnDefinition = "boolean default false")
	@Builder.Default
	private boolean fixed = false;

	@ElementCollection
	@CollectionTable(name = "category_tags", joinColumns = @JoinColumn(name = "category_id"))
	@Column(name = "tag")
	@Builder.Default
	private Set<String> tags = new HashSet<>();
}
