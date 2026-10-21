package it.glknet.financial_manager.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AuthInterceptorTest {

	private final RestClient.Builder builder = RestClient.builder();
	private final MockRestServiceServer userService = MockRestServiceServer.bindTo(builder).build();
	private final AuthInterceptor interceptor = new AuthInterceptor(builder, "http://user");

	private MockHttpServletResponse call(MockHttpServletRequest request) {
		MockHttpServletResponse response = new MockHttpServletResponse();
		boolean ok = interceptor.preHandle(request, response, new Object());
		assertEquals(ok, response.getStatus() == 200, "passa solo se non ha impostato un errore");
		return response;
	}

	private MockHttpServletRequest withToken(String token) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/transactions");
		if (token != null) {
			request.addHeader("Authorization", "Bearer " + token);
		}
		return request;
	}

	@Test
	void validTokenSetsUserIdFromUserService() {
		UUID id = UUID.randomUUID();
		userService.expect(requestTo("http://user/api/auth/me")).andExpect(header("Authorization", "Bearer good"))
				.andRespond(withSuccess("{\"userId\":\"" + id + "\",\"username\":\"a\",\"token\":\"good\"}",
						MediaType.APPLICATION_JSON));
		MockHttpServletRequest request = withToken("good");
		assertEquals(200, call(request).getStatus());
		assertEquals(id, request.getAttribute(AuthInterceptor.USER_ID));
	}

	@Test
	void missingTokenIs401WithoutCallingUserService() {
		assertEquals(401, call(withToken(null)).getStatus());
		userService.verify();
	}

	@Test
	void rejectedTokenIs401() {
		userService.expect(requestTo("http://user/api/auth/me")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
		assertEquals(401, call(withToken("bad")).getStatus());
	}

	@Test
	void userServiceDownIs503() {
		userService.expect(requestTo("http://user/api/auth/me")).andRespond(withServerError());
		assertEquals(503, call(withToken("x")).getStatus());
	}

	@Test
	void preflightPassesWithoutToken() {
		MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/transactions");
		request.addHeader("Origin", "http://localhost:3000");
		request.addHeader("Access-Control-Request-Method", "POST");
		assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
		assertFalse(request.getAttribute(AuthInterceptor.USER_ID) != null);
	}
}
