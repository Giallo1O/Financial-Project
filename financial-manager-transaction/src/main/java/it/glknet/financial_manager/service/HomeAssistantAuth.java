package it.glknet.financial_manager.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import it.glknet.financial_manager.model.HomeAssistantLink;
import it.glknet.financial_manager.repository.HomeAssistantLinkRepository;

/**
 * Collega l'account FinManager di una persona al suo account Home Assistant con l'OAuth2 di HA (lo stesso login
 * delle app ufficiali): FinManager apre la pagina di login di HA, HA rimanda a /ha-callback con un codice monouso,
 * il server lo scambia con un refresh token (salvato cifrato) da cui ricava access token di 30 minuti.
 * client_id e' l'indirizzo di FinManager e redirect_uri sta sullo stesso host, come chiede HA.
 */
@Service
public class HomeAssistantAuth {

	private static final Logger log = LoggerFactory.getLogger(HomeAssistantAuth.class);
	private static final Duration PENDING_TTL = Duration.ofMinutes(10);
	private static final Duration EARLY_REFRESH = Duration.ofSeconds(60);
	private static final SecureRandom RANDOM = new SecureRandom();

	/** Login iniziato e non ancora concluso, identificato dallo state. */
	record Pending(UUID userId, String baseUrl, String clientId, String redirectUri, Instant expires) {
	}

	record Access(String token, Instant expires) {
	}

	public record TodoList(String entityId, String name) {
	}

	public record Status(boolean available, boolean linked, boolean valid, String url, String entityId,
			LocalDateTime linkedAt) {
	}

	// ponytail: stato dei login e cache dei token in memoria, va bene con una sola replica del servizio;
	// con piu' repliche spostarli nel database
	private final Map<String, Pending> pending = new ConcurrentHashMap<>();
	private final Map<UUID, Access> access = new ConcurrentHashMap<>();

	private final HomeAssistantLinkRepository links;
	private final TokenCipher cipher;
	private final RestClient http;
	private final Clock clock;

	@Autowired
	public HomeAssistantAuth(HomeAssistantLinkRepository links, TokenCipher cipher) {
		this(links, cipher, RestClient.builder().requestFactory(timeouts()), Clock.systemUTC());
	}

	HomeAssistantAuth(HomeAssistantLinkRepository links, TokenCipher cipher, RestClient.Builder builder, Clock clock) {
		this.links = links;
		this.cipher = cipher;
		this.clock = clock;
		this.http = builder.build();
	}

	private static SimpleClientHttpRequestFactory timeouts() {
		SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
		timeouts.setConnectTimeout(Duration.ofSeconds(4));
		timeouts.setReadTimeout(Duration.ofSeconds(10));
		return timeouts;
	}

	// ---------------------------------------------------------------- collegamento

	/** Prepara il login: restituisce l'indirizzo della pagina di login di HA da aprire nel popup. */
	public String start(UUID userId, String haUrl, String appOrigin) {
		if (!cipher.enabled()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Collegamento non disponibile: manca la chiave APP_SECRET_KEY sul server");
		}
		String base = cleanUrl(haUrl, "l'indirizzo di Home Assistant");
		String origin = cleanUrl(appOrigin, "l'indirizzo di FinManager");
		String clientId = origin + "/";
		String redirectUri = origin + "/ha-callback";
		String state = randomToken();
		Instant now = clock.instant();
		pending.values().removeIf(p -> p.expires().isBefore(now));
		pending.put(state, new Pending(userId, base, clientId, redirectUri, now.plus(PENDING_TTL)));
		return base + "/auth/authorize?response_type=code&client_id=" + enc(clientId) + "&redirect_uri="
				+ enc(redirectUri) + "&state=" + enc(state);
	}

	/**
	 * Conclude il login con il codice che HA ha mandato a /ha-callback. Non richiede la sessione FinManager: lo state
	 * (casuale, monouso, valido 10 minuti) dice gia' chi ha iniziato il login. Cosi' funziona anche se sul telefono
	 * il login si apre in un'altra finestra senza la sessione.
	 */
	public void finish(String state, String code) {
		Pending p = state == null ? null : pending.remove(state);
		if (p == null || p.expires().isBefore(clock.instant())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Collegamento scaduto o gia' usato: riprova dal Profilo");
		}
		if (code == null || code.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Home Assistant non ha concesso l'accesso");
		}
		Map<String, Object> tokens = tokenRequest(p.baseUrl(),
				form("grant_type", "authorization_code", "code", code, "client_id", p.clientId()));
		Object refresh = tokens.get("refresh_token");
		if (refresh == null) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Home Assistant non ha restituito il token");
		}
		HomeAssistantLink link = links.findById(p.userId()).orElseGet(HomeAssistantLink::new);
		String oldRefresh = link.getRefreshToken() != null && !link.isRevoked() ? link.getRefreshToken() : null;
		link.setUserId(p.userId());
		link.setBaseUrl(p.baseUrl());
		link.setClientId(p.clientId());
		link.setRefreshToken(cipher.encrypt(String.valueOf(refresh)));
		link.setRevoked(false);
		link.setLinkedAt(LocalDateTime.now(clock));
		remember(p.userId(), tokens);
		if (link.getEntityId() == null) {
			link.setEntityId(guessShoppingList(todoLists(p.baseUrl(), access.get(p.userId()).token())));
		}
		links.save(link);
		if (oldRefresh != null) {
			revokeQuietly(link.getBaseUrl(), cipher.decrypt(oldRefresh)); // ricollegato: il vecchio token non serve piu'
		}
	}

	/** Scollega: revoca il token in HA (se raggiungibile) e lo cancella da FinManager. */
	public void unlink(UUID userId) {
		links.findById(userId).ifPresent(l -> {
			if (!l.isRevoked()) {
				revokeQuietly(l.getBaseUrl(), cipher.decrypt(l.getRefreshToken()));
			}
			links.delete(l);
		});
		access.remove(userId);
	}

	public Status status(UUID userId) {
		Optional<HomeAssistantLink> l = links.findById(userId);
		return new Status(cipher.enabled(), l.isPresent(), l.map(x -> !x.isRevoked()).orElse(false),
				l.map(HomeAssistantLink::getBaseUrl).orElse(null), l.map(HomeAssistantLink::getEntityId).orElse(null),
				l.map(HomeAssistantLink::getLinkedAt).orElse(null));
	}

	// ---------------------------------------------------------------- uso

	/** Collegamento valido con una lista scelta, oppure vuoto. */
	public Optional<HomeAssistantLink> usableLink(UUID userId) {
		return links.findById(userId).filter(l -> !l.isRevoked() && l.getEntityId() != null);
	}

	/** Access token valido (rinnovato quando sta per scadere). */
	public String accessToken(UUID userId) {
		Access a = access.get(userId);
		if (a != null && a.expires().isAfter(clock.instant().plus(EARLY_REFRESH))) {
			return a.token();
		}
		HomeAssistantLink link = links.findById(userId).filter(l -> !l.isRevoked())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
						"Account Home Assistant non collegato: collegalo dal Profilo"));
		try {
			remember(userId, tokenRequest(link.getBaseUrl(), form("grant_type", "refresh_token", "refresh_token",
					cipher.decrypt(link.getRefreshToken()), "client_id", link.getClientId())));
		} catch (HaRejected e) {
			link.setRevoked(true);
			links.save(link);
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Home Assistant ha revocato il collegamento: ricollegalo dal Profilo");
		}
		return access.get(userId).token();
	}

	/** Dimentica l'access token in cache (es. HA ha risposto 401): al prossimo uso si rinnova. */
	public void forgetAccess(UUID userId) {
		access.remove(userId);
	}

	public List<TodoList> todoLists(UUID userId) {
		HomeAssistantLink link = links.findById(userId).filter(l -> !l.isRevoked())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Account Home Assistant non collegato"));
		return todoLists(link.getBaseUrl(), accessToken(userId));
	}

	public Status chooseList(UUID userId, String entityId) {
		if (todoLists(userId).stream().noneMatch(t -> t.entityId().equals(entityId))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lista non trovata in Home Assistant");
		}
		HomeAssistantLink link = links.findById(userId).orElseThrow();
		link.setEntityId(entityId);
		links.save(link);
		return status(userId);
	}

	// ---------------------------------------------------------------- dettagli

	@SuppressWarnings("unchecked")
	private List<TodoList> todoLists(String baseUrl, String token) {
		try {
			List<Map<String, Object>> states = http.get().uri(baseUrl + "/api/states")
					.header("Authorization", "Bearer " + token).retrieve().body(List.class);
			List<TodoList> out = new ArrayList<>();
			for (Map<String, Object> s : states == null ? List.<Map<String, Object>>of() : states) {
				String id = String.valueOf(s.get("entity_id"));
				if (id.startsWith("todo.")) {
					Map<String, Object> attrs = (Map<String, Object>) s.getOrDefault("attributes", Map.of());
					out.add(new TodoList(id, String.valueOf(attrs.getOrDefault("friendly_name", id))));
				}
			}
			out.sort(Comparator.comparing(TodoList::name, String.CASE_INSENSITIVE_ORDER));
			return out;
		} catch (RestClientException e) {
			log.warn("Home Assistant /api/states fallito: {}", e.getMessage());
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Home Assistant non risponde");
		}
	}

	/** La Shopping list se c'e', altrimenti una lista con "spesa" nel nome, altrimenti nessuna (si sceglie a mano). */
	static String guessShoppingList(List<TodoList> lists) {
		return lists.stream()
				.filter(t -> t.entityId().equals("todo.shopping_list")
						|| t.entityId().contains("spesa") || t.name().toLowerCase(Locale.ROOT).contains("spesa")
						|| t.name().toLowerCase(Locale.ROOT).contains("shopping"))
				.map(TodoList::entityId).findFirst().orElse(null);
	}

	/** Errore 4xx di HA sul token: codice o refresh token non validi. */
	static class HaRejected extends RuntimeException {
		HaRejected(String m) {
			super(m);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> tokenRequest(String baseUrl, MultiValueMap<String, String> body) {
		try {
			Map<String, Object> res = http.post().uri(baseUrl + "/auth/token")
					.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body).retrieve().body(Map.class);
			if (res == null || res.get("access_token") == null) {
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Risposta di Home Assistant non valida");
			}
			return res;
		} catch (HttpClientErrorException e) {
			log.warn("Home Assistant /auth/token rifiutato: {}", e.getStatusCode());
			if ("refresh_token".equals(body.getFirst("grant_type"))) {
				throw new HaRejected(e.getMessage());
			}
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Home Assistant ha rifiutato il collegamento: riprova dal Profilo");
		} catch (RestClientException e) {
			log.warn("Home Assistant /auth/token irraggiungibile: {}", e.getMessage());
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"Il server di FinManager non raggiunge Home Assistant a " + baseUrl);
		}
	}

	private void remember(UUID userId, Map<String, Object> tokens) {
		long seconds = tokens.get("expires_in") instanceof Number n ? n.longValue() : 1800;
		access.put(userId, new Access(String.valueOf(tokens.get("access_token")), clock.instant().plusSeconds(seconds)));
	}

	private void revokeQuietly(String baseUrl, String refreshToken) {
		try {
			http.post().uri(baseUrl + "/auth/revoke").contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form("token", refreshToken)).retrieve().toBodilessEntity();
		} catch (RestClientException e) {
			log.info("Revoca del token in Home Assistant non riuscita (HA irraggiungibile?): {}", e.getMessage());
		}
	}

	/** Solo http/https, senza percorso, query o credenziali; senza la barra finale. */
	static String cleanUrl(String raw, String what) {
		try {
			URI u = URI.create(raw == null ? "" : raw.trim());
			String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
			boolean onlyRoot = u.getRawPath() == null || u.getRawPath().isEmpty() || u.getRawPath().equals("/");
			if (!(scheme.equals("http") || scheme.equals("https")) || u.getHost() == null || u.getRawUserInfo() != null
					|| u.getRawQuery() != null || u.getRawFragment() != null || !onlyRoot) {
				throw new IllegalArgumentException();
			}
			return scheme + "://" + u.getHost().toLowerCase(Locale.ROOT) + (u.getPort() > 0 ? ":" + u.getPort() : "");
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Indirizzo non valido per " + what + " (es. https://homeassistant.casa.it)");
		}
	}

	private static MultiValueMap<String, String> form(String... kv) {
		MultiValueMap<String, String> m = new LinkedMultiValueMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.add(kv[i], kv[i + 1]);
		}
		return m;
	}

	private static String randomToken() {
		byte[] b = new byte[32];
		RANDOM.nextBytes(b);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}
}
