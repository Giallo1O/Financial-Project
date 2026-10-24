package it.glknet.financial_manager.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import it.glknet.financial_manager.model.HomeAssistantLink;

/**
 * La lista della spesa di Home Assistant (un'entita' todo, di solito la Shopping list), letta e scritta con i servizi
 * todo.* dell'API REST con l'account HA collegato dalla persona (vedi {@link HomeAssistantAuth}). Nessuna copia
 * locale: HA e' l'unica fonte, cosi' voce, app di HA e FinManager vedono le stesse voci.
 */
@Component
public class HomeAssistantTodo {

	private static final Logger log = LoggerFactory.getLogger(HomeAssistantTodo.class);

	/** Quantita' scritta nel testo della voce, es. "Latte (2 l)" o "Mele (1,5 kg)". */
	private static final Pattern QTY = Pattern.compile("^(.*?)\\s*\\((\\d+(?:[.,]\\d+)?)\\s*([a-zA-Z]{1,5})?\\)\\s*$");

	public record Item(String uid, String summary, boolean completed) {
	}

	/** Testo di una voce scomposto: "Latte (2 l)" -> Latte, 2, l. Senza parentesi: quantita' 1 pz. */
	public record Text(String name, BigDecimal quantity, String unit) {

		public static Text parse(String summary) {
			String s = summary == null ? "" : summary.trim();
			Matcher m = QTY.matcher(s);
			if (m.matches() && !m.group(1).isBlank()) {
				String unit = m.group(3) == null ? "pz" : m.group(3).toLowerCase();
				return new Text(m.group(1).trim(), new BigDecimal(m.group(2).replace(',', '.')), unit);
			}
			return new Text(s, BigDecimal.ONE, "pz");
		}

		public String format() {
			if (quantity.compareTo(BigDecimal.ONE) == 0 && "pz".equals(unit)) {
				return name;
			}
			return name + " (" + quantity.stripTrailingZeros().toPlainString().replace('.', ',') + " " + unit + ")";
		}
	}

	private final HomeAssistantAuth auth;
	private final RestClient http;

	@org.springframework.beans.factory.annotation.Autowired
	public HomeAssistantTodo(HomeAssistantAuth auth) {
		this(auth, RestClient.builder().requestFactory(timeouts()));
	}

	HomeAssistantTodo(HomeAssistantAuth auth, RestClient.Builder builder) {
		this.auth = auth;
		this.http = builder.build();
	}

	private static SimpleClientHttpRequestFactory timeouts() {
		SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
		timeouts.setConnectTimeout(Duration.ofSeconds(3));
		timeouts.setReadTimeout(Duration.ofSeconds(8));
		return timeouts;
	}

	/** La persona ha collegato il suo account HA e scelto la lista della spesa. */
	public boolean enabled(UUID userId) {
		return auth.usableLink(userId).isPresent();
	}

	@SuppressWarnings("unchecked")
	public List<Item> items(UUID userId) {
		String entity = entity(userId);
		Map<String, Object> body = new HashMap<>();
		body.put("entity_id", entity);
		body.put("status", List.of("needs_action", "completed"));
		Map<String, Object> res = (Map<String, Object>) call(userId, "get_items?return_response", body);
		Map<String, Object> byEntity = (Map<String, Object>) (res == null ? null : res.get("service_response"));
		Map<String, Object> list = (Map<String, Object>) (byEntity == null ? null : byEntity.get(entity));
		List<Item> out = new ArrayList<>();
		for (Object o : list == null ? List.of() : (List<Object>) list.get("items")) {
			Map<String, Object> i = (Map<String, Object>) o;
			out.add(new Item(String.valueOf(i.get("uid")), String.valueOf(i.get("summary")),
					"completed".equals(i.get("status"))));
		}
		return out;
	}

	public void add(UUID userId, String summary) {
		call(userId, "add_item", Map.of("entity_id", entity(userId), "item", summary));
	}

	/** rename e completed null = non cambiano. */
	public void update(UUID userId, String uid, String rename, Boolean completed) {
		Map<String, Object> body = new HashMap<>();
		body.put("entity_id", entity(userId));
		body.put("item", uid);
		if (rename != null) {
			body.put("rename", rename);
		}
		if (completed != null) {
			body.put("status", completed ? "completed" : "needs_action");
		}
		call(userId, "update_item", body);
	}

	public void remove(UUID userId, String uid) {
		call(userId, "remove_item", Map.of("entity_id", entity(userId), "item", List.of(uid)));
	}

	public void removeCompleted(UUID userId) {
		call(userId, "remove_completed_items", Map.of("entity_id", entity(userId)));
	}

	private String entity(UUID userId) {
		return auth.usableLink(userId).map(HomeAssistantLink::getEntityId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
						"Collega Home Assistant e scegli la lista della spesa dal Profilo"));
	}

	@SuppressWarnings("unchecked")
	/**
	 * La risposta e' un oggetto solo con return_response (get_items); gli altri servizi restituiscono l'elenco degli
	 * stati cambiati, che non serve: per questo il tipo e' generico.
	 */
	private Object call(UUID userId, String service, Map<String, Object> body) {
		String baseUrl = auth.usableLink(userId).map(HomeAssistantLink::getBaseUrl)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Home Assistant non collegato"));
		try {
			return http.post().uri(baseUrl + "/api/services/todo/" + service)
					.header("Authorization", "Bearer " + auth.accessToken(userId)).body(body).retrieve().body(Object.class);
		} catch (HttpClientErrorException.Unauthorized e) {
			auth.forgetAccess(userId); // token non piu' valido: al prossimo giro se ne chiede uno nuovo
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Home Assistant ha rifiutato l'accesso: riprova");
		} catch (RestClientException e) {
			log.warn("Home Assistant todo.{} fallito: {}", service, e.getMessage());
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"Home Assistant non risponde: la lista di casa non e' disponibile ora");
		}
	}
}
