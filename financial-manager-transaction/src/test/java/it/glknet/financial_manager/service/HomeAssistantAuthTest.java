package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import it.glknet.financial_manager.model.HomeAssistantLink;
import it.glknet.financial_manager.repository.HomeAssistantLinkRepository;

class HomeAssistantAuthTest {

	private static final String HA = "https://ha.casa.it";
	private static final String APP = "https://fin.casa.it";

	private final Map<UUID, HomeAssistantLink> store = new HashMap<>();
	private final HomeAssistantLinkRepository links = mock(HomeAssistantLinkRepository.class);
	private final TokenCipher cipher = new TokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
	private final RestClient.Builder builder = RestClient.builder();
	private final MockRestServiceServer ha = MockRestServiceServer.bindTo(builder).build();
	private final MutableClock clock = new MutableClock();
	private final HomeAssistantAuth auth = new HomeAssistantAuth(links, cipher, builder, clock);
	private final UUID andrea = UUID.randomUUID();

	HomeAssistantAuthTest() {
		when(links.findById(any())).thenAnswer(i -> Optional.ofNullable(store.get(i.<UUID>getArgument(0))));
		when(links.save(any())).thenAnswer(i -> {
			HomeAssistantLink l = i.getArgument(0);
			store.put(l.getUserId(), l);
			return l;
		});
	}

	private String stateOf(String authorizeUrl) {
		return authorizeUrl.substring(authorizeUrl.indexOf("&state=") + 7);
	}

	private String link() {
		String url = auth.start(andrea, HA + "/", APP);
		ha.expect(requestTo(HA + "/auth/token")).andExpect(method(HttpMethod.POST))
				.andExpect(content().string("grant_type=authorization_code&code=abc&client_id=https%3A%2F%2Ffin.casa.it%2F"))
				.andRespond(withSuccess("{\"access_token\":\"acc1\",\"expires_in\":1800,\"refresh_token\":\"ref1\"}",
						MediaType.APPLICATION_JSON));
		ha.expect(requestTo(HA + "/api/states")).andExpect(header("Authorization", "Bearer acc1"))
				.andRespond(withSuccess("[{\"entity_id\":\"light.cucina\"},{\"entity_id\":\"todo.lista_della_spesa\","
						+ "\"attributes\":{\"friendly_name\":\"Lista della spesa\"}}]", MediaType.APPLICATION_JSON));
		auth.finish(stateOf(url), "abc");
		return url;
	}

	@Test
	void loginPageUsesTheAppAddressAsClientAndCallback() {
		String url = auth.start(andrea, "https://HA.casa.it:8123/", APP);
		assertTrue(url.startsWith("https://ha.casa.it:8123/auth/authorize?response_type=code"));
		assertTrue(url.contains("client_id=https%3A%2F%2Ffin.casa.it%2F"));
		assertTrue(url.contains("redirect_uri=https%3A%2F%2Ffin.casa.it%2Fha-callback"));
		for (String bad : new String[] { "ftp://ha", "https://ha/lovelace", "https://user:pw@ha", "ha.casa.it" }) {
			assertThrows(ResponseStatusException.class, () -> auth.start(andrea, bad, APP));
		}
	}

	@Test
	void finishingTheLoginStoresTheEncryptedRefreshTokenAndFindsTheShoppingList() {
		String url = link();
		ha.verify();

		HomeAssistantLink saved = store.get(andrea);
		assertNotEquals("ref1", saved.getRefreshToken()); // mai in chiaro nel database
		assertEquals("ref1", cipher.decrypt(saved.getRefreshToken()));
		assertEquals("todo.lista_della_spesa", saved.getEntityId());
		assertEquals("acc1", auth.accessToken(andrea)); // dalla cache, senza chiamare HA
		// lo state vale una volta sola
		assertThrows(ResponseStatusException.class, () -> auth.finish(stateOf(url), "abc"));
	}

	@Test
	void expiredAccessTokenIsRenewedAndARevokedLinkMustBeLinkedAgain() {
		link();
		clock.advance(Duration.ofMinutes(31));
		ha.reset();
		ha.expect(requestTo(HA + "/auth/token"))
				.andExpect(content().string("grant_type=refresh_token&refresh_token=ref1&client_id=https%3A%2F%2Ffin.casa.it%2F"))
				.andRespond(withSuccess("{\"access_token\":\"acc2\",\"expires_in\":1800}", MediaType.APPLICATION_JSON));
		assertEquals("acc2", auth.accessToken(andrea));

		clock.advance(Duration.ofMinutes(31));
		ha.reset();
		ha.expect(requestTo(HA + "/auth/token")).andRespond(withStatus(HttpStatus.BAD_REQUEST));
		ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> auth.accessToken(andrea));
		assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
		assertTrue(store.get(andrea).isRevoked());
		assertFalse(auth.status(andrea).valid());
		assertTrue(auth.usableLink(andrea).isEmpty());
	}

	@Test
	void withoutTheSecretKeyLinkingIsUnavailable() {
		HomeAssistantAuth off = new HomeAssistantAuth(links, new TokenCipher(""), RestClient.builder(), clock);
		assertFalse(off.status(andrea).available());
		assertThrows(ResponseStatusException.class, () -> off.start(andrea, HA, APP));
	}

	@Test
	void guessesTheShoppingList() {
		assertEquals("todo.shopping_list", HomeAssistantAuth.guessShoppingList(List.of(
				new HomeAssistantAuth.TodoList("todo.lavoro", "Lavoro"),
				new HomeAssistantAuth.TodoList("todo.shopping_list", "Shopping List"))));
		assertEquals(null, HomeAssistantAuth.guessShoppingList(List.of(new HomeAssistantAuth.TodoList("todo.x", "X"))));
	}

	private static class MutableClock extends Clock {
		private Instant now = Instant.parse("2026-10-24T10:00:00Z");

		void advance(Duration d) {
			now = now.plus(d);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}
	}
}
