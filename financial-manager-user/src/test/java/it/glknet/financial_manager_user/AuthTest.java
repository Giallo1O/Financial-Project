package it.glknet.financial_manager_user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import it.glknet.financial_manager_user.controller.AuthController;
import it.glknet.financial_manager_user.dto.LoginRequest;
import it.glknet.financial_manager_user.dto.RegisterRequest;
import it.glknet.financial_manager_user.model.User;
import it.glknet.financial_manager_user.repository.UserRepository;
import it.glknet.financial_manager_user.service.UserService;

class AuthTest {

	private final UserRepository repo = mock(UserRepository.class);
	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
	private final UserService service = new UserService(repo, encoder);

	@Test
	void secondLoginKeepsTheSameTokenSoOtherDevicesStayLoggedIn() {
		User u = User.builder().username("a").passwordHash(encoder.encode("pw")).token("tok").build();
		when(repo.findByUsername("a")).thenReturn(Optional.of(u));

		assertEquals("tok", service.login(new LoginRequest("a", "pw")).getToken());
		assertEquals("tok", service.login(new LoginRequest("a", "pw")).getToken());
	}

	@Test
	void registerWithBlankUsernameOrPasswordIsBadRequest() {
		UserService svc = mock(UserService.class);
		AuthController controller = new AuthController(svc);

		assertEquals(HttpStatus.BAD_REQUEST, controller.register(new RegisterRequest("", "pw")).getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST, controller.register(new RegisterRequest("a", null)).getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST, controller.register(new RegisterRequest("a", "  ")).getStatusCode());
		verify(svc, never()).register(any());
	}
}
