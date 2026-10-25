package it.glknet.financial_manager.model;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
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

/**
 * Un conto/Space o un IBAN di controparte visto negli estratti conto importati: sono le scelte del menu "collegato a"
 * del salvadanaio.
 */
@Entity
@Table(name = "import_sources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportSource {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	/** "SPACE" o "IBAN". */
	@Column(nullable = false)
	private String type;

	/** Nome dello Space, oppure IBAN normalizzato. */
	@Column(name = "source_key", nullable = false)
	private String key;

	/** Nome dello Space, oppure nome della controparte. */
	private String label;

	@Column(name = "last_seen")
	private LocalDate lastSeen;
}
