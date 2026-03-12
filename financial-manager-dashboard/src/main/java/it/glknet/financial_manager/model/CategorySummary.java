package it.glknet.financial_manager.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "category_summaries")
public class CategorySummary {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id; // Nuova chiave primaria indipendente

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(name = "category_id", nullable = false)
	private UUID categoryId;
	private BigDecimal totalAmount = BigDecimal.ZERO;

	private Long transactionCount = 0L;

	@Column(name = "month_period")
	private Integer month;

	@Column(name = "year_period")
	private Integer year;

	private LocalDateTime lastUpdate;

}
