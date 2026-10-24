package it.glknet.financial_manager.config;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Su ogni /api/** chiede all'user-service di chi e' il token "Authorization: Bearer ..."
 * e mette lo userId verificato nell'attributo {@link #USER_ID} della richiesta.
 * I controller usano SOLO questo valore, mai quello mandato dal client.
 * Configura anche il CORS (origini ammesse da cors.allowed-origins, separate da virgola).
 */
// ponytail: una chiamata HTTP all'user-service per richiesta; aggiungere cache a TTL breve se la latenza pesa.
@Component
public class AuthInterceptor implements HandlerInterceptor, WebMvcConfigurer {

	public static final String USER_ID = "userId";
	public static final String USERNAME = "username";

	private final RestClient userService;

	@Value("${cors.allowed-origins:http://localhost:3000}")
	private String[] allowedOrigins;

	@Autowired
	public AuthInterceptor(@Value("${user-service.url:http://localhost:8082}") String userServiceUrl) {
		this(RestClient.builder(), userServiceUrl);
	}

	AuthInterceptor(RestClient.Builder builder, String userServiceUrl) {
		this.userService = builder.baseUrl(userServiceUrl).build();
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**").allowedOrigins(allowedOrigins).allowedMethods("GET", "POST", "PUT", "DELETE")
				.allowedHeaders("*");
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this).addPathPatterns("/api/**")
				.excludePathPatterns(it.glknet.financial_manager.controller.HomeAssistantController.CALLBACK_PATH);
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (CorsUtils.isPreFlightRequest(request)) {
			return true;
		}
		String authorization = request.getHeader("Authorization");
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return false;
		}
		try {
			Map<?, ?> me = userService.get().uri("/api/auth/me").header("Authorization", authorization)
					.retrieve().body(Map.class);
			request.setAttribute(USER_ID, UUID.fromString(String.valueOf(me.get("userId"))));
			request.setAttribute(USERNAME, String.valueOf(me.get("username")));
			return true;
		} catch (HttpClientErrorException e) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		} catch (RestClientException | IllegalArgumentException | NullPointerException e) {
			response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
		return false;
	}
}
