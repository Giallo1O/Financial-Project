package it.glknet.financial_manager.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * La lista della spesa di Home Assistant (un'entita' todo, di solito la Shopping list), letta e scritta con i servizi
 * todo.* dell'API REST. Nessuna copia locale: HA e' l'unica fonte, cosi' voce, app di HA e FinManager vedono le
 * stesse voci. Attiva solo se sono impostati URL e token.
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

	private final RestClient client;
	private final String entity;

	public HomeAssistantTodo(@Value("${home-assistant.url:}") String url, @Value("${home-assistant.token:}") String token,
			@Value("${home-assistant.shopping-entity:todo.lista_della_spesa}") String entity) {
		this.entity = entity;
		if (url.isBlank() || token.isBlank()) {
			this.client = null;
			return;
		}
		SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
		timeouts.setConnectTimeout(Duration.ofSeconds(3));
		timeouts.setReadTimeout(Duration.ofSeconds(8));
		this.client = RestClient.builder().baseUrl(url.replaceAll("/+$", "")).requestFactory(timeouts)
				.defaultHeader("Authorization", "Bearer " + token).build();
	}

	public boolean enabled() {
		return client != null;
	}

	/** Voci da comprare e gia' prese, nell'ordine di Home Assistant. */
	@SuppressWarnings("unchecked")
	public List<Item> items() {
		Map<String, Object> body = new HashMap<>();
		body.put("entity_id", entity);
		body.put("status", List.of("needs_action", "completed"));
		Map<String, Object> res = call("get_items?return_response", body);
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

	public void add(String summary) {
		call("add_item", Map.of("entity_id", entity, "item", summary));
	}

	/** rename e completed null = non cambiano. */
	public void update(String uid, String rename, Boolean completed) {
		Map<String, Object> body = new HashMap<>();
		body.put("entity_id", entity);
		body.put("item", uid);
		if (rename != null) {
			body.put("rename", rename);
		}
		if (completed != null) {
			body.put("status", completed ? "completed" : "needs_action");
		}
		call("update_item", body);
	}

	public void remove(String uid) {
		call("remove_item", Map.of("entity_id", entity, "item", List.of(uid)));
	}

	public void removeCompleted() {
		call("remove_completed_items", Map.of("entity_id", entity));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> call(String service, Map<String, Object> body) {
		if (client == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Home Assistant non configurato");
		}
		try {
			return client.post().uri("/api/services/todo/" + service).body(body).retrieve().body(Map.class);
		} catch (RestClientException e) {
			log.warn("Home Assistant todo.{} fallito: {}", service, e.getMessage());
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"Home Assistant non risponde: la lista di casa non e' disponibile ora");
		}
	}
}
