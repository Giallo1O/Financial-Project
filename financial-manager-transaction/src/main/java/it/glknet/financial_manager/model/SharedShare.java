package it.glknet.financial_manager.model;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** La parte di una spesa che spetta a una persona. */
@Entity
@Table(name = "shared_shares")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SharedShare {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "expense_id", nullable = false)
	private UUID expenseId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal amount;
}
