package it.glknet.financial_manager.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import it.glknet.financial_manager.config.AuthInterceptor;
import it.glknet.financial_manager.service.HomeAssistantAuth;
import lombok.RequiredArgsConstructor;

/** Collegamento dell'account Home Assistant dal Profilo. */
@RestController
@RequestMapping("/api/home-assistant")
@RequiredArgsConstructor
public class HomeAssistantController {

	/** Unico endpoint senza sessione FinManager: lo protegge lo state monouso (vedi AuthInterceptor). */
	public static final String CALLBACK_PATH = "/api/home-assistant/callback";

	private final HomeAssistantAuth auth;

	public record StartRequest(String url, String origin) {
	}

	public record CallbackRequest(String state, String code) {
	}

	public record ListRequest(String entityId) {
	}

	@GetMapping
	public HomeAssistantAuth.Status status(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
		return auth.status(userId);
	}

	/** Restituisce l'indirizzo della pagina di login di HA da aprire nel popup. */
	@PostMapping("/link")
	public Map<String, String> start(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestBody StartRequest req) {
		return Map.of("authorizeUrl", auth.start(userId, req.url(), req.origin()));
	}

	@PostMapping("/callback")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void callback(@RequestBody CallbackRequest req) {
		auth.finish(req.state(), req.code());
	}

	@DeleteMapping("/link")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unlink(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
		auth.unlink(userId);
	}

	@GetMapping("/lists")
	public List<HomeAssistantAuth.TodoList> lists(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
		return auth.todoLists(userId);
	}

	@PutMapping("/list")
	public HomeAssistantAuth.Status chooseList(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestBody ListRequest req) {
		return auth.chooseList(userId, req.entityId());
	}
}
