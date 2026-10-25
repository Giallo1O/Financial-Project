package it.glknet.financial_manager.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Versamento (importo positivo) o prelievo (negativo) di un salvadanaio arrivato con l'import dell'estratto conto.
 * Serve a non contarlo due volte se lo stesso estratto conto viene importato di nuovo.
 */
@Entity
@Table(name = "savings_movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsMovement {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "goal_id", nullable = false)
	private UUID goalId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "movement_date", nullable = false)
	private LocalDate date;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	private String description;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
