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

/** Una riga di scontrino. Gli sconti sono righe con totalPrice negativo. */
@Entity
@Table(name = "receipt_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReceiptItem {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "receipt_id", nullable = false)
	private UUID receiptId;

	/** Testo come stampato sullo scontrino. */
	@Column(name = "raw_name")
	private String rawName;

	/** Nome leggibile e normalizzato: lo stesso prodotto ha lo stesso nome tra scontrini diversi. */
	@Column(nullable = false)
	private String name;

	@Column(name = "food_category")
	private String foodCategory;

	@Column(precision = 12, scale = 3)
	private BigDecimal quantity;

	/** pz, kg, l */
	private String unit;

	@Column(name = "unit_price", precision = 12, scale = 4)
	private BigDecimal unitPrice;

	@Column(name = "total_price", precision = 12, scale = 2)
	private BigDecimal totalPrice;
}
