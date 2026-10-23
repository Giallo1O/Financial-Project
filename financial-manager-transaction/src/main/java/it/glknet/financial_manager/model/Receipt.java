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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Uno scontrino della spesa; la spesa corrispondente e' la transazione {@link #transactionId}. */
@Entity
@Table(name = "receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Receipt {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "store_name", nullable = false)
	private String storeName;

	@Column(name = "purchase_date", nullable = false)
	private LocalDate purchaseDate;

	@Column(nullable = false)
	private BigDecimal total;

	@Column(name = "transaction_id")
	private UUID transactionId;

	/** false se lo scontrino e' stato collegato a un movimento gia' esistente (es. importato dalla banca). Null = vecchi dati: la spesa l'ha creata lo scontrino. */
	@Column(name = "transaction_created")
	private Boolean transactionCreated;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
