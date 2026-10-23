package it.glknet.financial_manager.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** "Se la descrizione contiene questa parola chiave, la categoria e' questa" (per utente). */
@Entity
@Table(name = "category_rules", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "keyword" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CategoryRule {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	/** Normalizzata (minuscolo, senza accenti, parole separate da spazio). */
	@Column(nullable = false)
	private String keyword;

	@Column(name = "category_id", nullable = false)
	private UUID categoryId;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
