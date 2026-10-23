package it.glknet.financial_manager.model;

import java.math.BigDecimal;
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

@Entity
@Table(name = "shopping_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ShoppingItem {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "list_id", nullable = false)
	private UUID listId;

	@Column(nullable = false)
	private String name;

	@Column(precision = 12, scale = 3)
	private BigDecimal quantity;

	/** pz, kg, l */
	private String unit;

	private boolean checked;

	@Column(name = "added_by")
	private String addedBy;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "checked_at")
	private LocalDateTime checkedAt;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
