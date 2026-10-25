package it.glknet.financial_manager.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "transactions")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Transaction {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String description;

	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	private TransactionType type;

	private UUID categoryId;

	/** Conto su cui e' registrato il movimento; null = nessun conto (dati precedenti ai conti). */
	private UUID accountId;

	private UUID userId;

	private LocalDate transactionDate;

	/**
	 * Ricorrenza da cui viene il movimento (confermata a mano o in automatico, oppure movimento della banca collegato
	 * alla scadenza): serve a non contare due volte la stessa spesa tra ricorrenze e import.
	 */
	private UUID recurringId;

	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
	}

}
