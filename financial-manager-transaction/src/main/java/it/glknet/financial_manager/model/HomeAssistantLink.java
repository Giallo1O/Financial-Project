package it.glknet.financial_manager.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Collegamento di un utente FinManager al suo account Home Assistant (OAuth2 di HA). Uno per utente. */
@Entity
@Table(name = "home_assistant_links")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HomeAssistantLink {

	@Id
	@Column(name = "user_id")
	private UUID userId;

	/** Indirizzo di HA usato sia dal browser (login) sia dal server (API), es. https://homeassistant.casa.it */
	@Column(name = "base_url", nullable = false)
	private String baseUrl;

	/** client_id OAuth2 (l'indirizzo di FinManager): serve uguale anche per rinnovare il token. */
	@Column(name = "client_id", nullable = false)
	private String clientId;

	/** Refresh token di HA cifrato con {@code TokenCipher}: non scade finche' non viene revocato. */
	@Column(name = "refresh_token", nullable = false, length = 2048)
	private String refreshToken;

	/** Lista todo di HA usata come lista della spesa di casa, es. todo.lista_della_spesa */
	@Column(name = "entity_id")
	private String entityId;

	/** true se HA ha rifiutato il refresh token (revocato o utente cancellato): va ricollegato. */
	private boolean revoked;

	@Column(name = "linked_at")
	private LocalDateTime linkedAt;
}
