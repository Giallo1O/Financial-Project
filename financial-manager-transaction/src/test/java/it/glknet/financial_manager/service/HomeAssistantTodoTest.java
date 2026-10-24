package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import it.glknet.financial_manager.model.HomeAssistantLink;

/** Le risposte sono quelle vere dell'API REST di Home Assistant. */
class HomeAssistantTodoTest {

	private static final String HA = "https://ha.casa.it";
	private static final String E = "todo.lista_della_spesa";

	private final UUID andrea = UUID.randomUUID();
	private final HomeAssistantAuth auth = mock(HomeAssistantAuth.class);
	private final RestClient.Builder builder = RestClient.builder();
	private final MockRestServiceServer ha = MockRestServiceServer.bindTo(builder).build();
	private final HomeAssistantTodo todo = new HomeAssistantTodo(auth, builder);

	HomeAssistantTodoTest() {
		when(auth.usableLink(andrea)).thenReturn(Optional.of(
				HomeAssistantLink.builder().userId(andrea).baseUrl(HA).entityId(E).build()));
		when(auth.accessToken(andrea)).thenReturn("acc");
	}

	@Test
	void readsItemsFromTheServiceResponse() {
		ha.expect(requestTo(HA + "/api/services/todo/get_items?return_response")).andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer acc"))
				.andRespond(withSuccess("{\"changed_states\":[],\"service_response\":{\"" + E + "\":{\"items\":["
						+ "{\"summary\":\"Latte (2 l)\",\"uid\":\"a1\",\"status\":\"needs_action\"},"
						+ "{\"summary\":\"Pane\",\"uid\":\"b2\",\"status\":\"completed\"}]}}}", MediaType.APPLICATION_JSON));

		List<HomeAssistantTodo.Item> items = todo.items(andrea);

		assertEquals(List.of(new HomeAssistantTodo.Item("a1", "Latte (2 l)", false),
				new HomeAssistantTodo.Item("b2", "Pane", true)), items);
	}

	@Test
	void changesAcceptTheListOfChangedStatesThatHaReturns() {
		ha.expect(requestTo(HA + "/api/services/todo/add_item"))
				.andExpect(content().json("{\"entity_id\":\"" + E + "\",\"item\":\"Farina 00\"}"))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
		ha.expect(requestTo(HA + "/api/services/todo/update_item"))
				.andExpect(content().json("{\"entity_id\":\"" + E + "\",\"item\":\"a1\",\"status\":\"completed\"}"))
				.andRespond(withSuccess("[{\"entity_id\":\"" + E + "\",\"state\":\"1\",\"attributes\":{}}]",
						MediaType.APPLICATION_JSON));
		ha.expect(requestTo(HA + "/api/services/todo/remove_item"))
				.andExpect(content().json("{\"entity_id\":\"" + E + "\",\"item\":[\"a1\"]}"))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		todo.add(andrea, "Farina 00");
		todo.update(andrea, "a1", null, true);
		todo.remove(andrea, "a1");

		ha.verify();
		assertTrue(todo.enabled(andrea));
	}
}
