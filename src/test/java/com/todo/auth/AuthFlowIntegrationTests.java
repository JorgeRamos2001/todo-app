package com.todo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.todo.auth.dto.AuthResponse;
import com.todo.auth.dto.LoginRequest;
import com.todo.auth.dto.RefreshRequest;
import com.todo.auth.dto.RegisterRequest;
import com.todo.auth.repository.RefreshTokenRepository;
import com.todo.auth.service.AuthService;
import com.todo.users.domain.User;
import com.todo.users.domain.UserProvider;
import com.todo.users.repository.UserRepository;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthFlowIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	UserRepository userRepository;

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	AuthService authService;

	@BeforeEach
	void cleanDatabase() {
		refreshTokenRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void registerCreatesUserAndReturnsTokens() throws Exception {
		AuthResponse tokens = register("Jorge@Example.com", "password123");

		assertThat(tokens.accessToken()).isNotBlank();
		assertThat(tokens.refreshToken()).isNotBlank();
		assertThat(tokens.tokenType()).isEqualTo("Bearer");
		assertThat(tokens.expiresIn()).isEqualTo(900);

		User user = userRepository.findByEmail("jorge@example.com").orElseThrow();
		assertThat(user.getProvider()).isEqualTo(UserProvider.LOCAL);
		assertThat(user.getPassword()).isNotEqualTo("password123");
	}

	@Test
	void registerRejectsDuplicateEmail() throws Exception {
		register("duplicate@example.com", "password123");

		registerRequest("duplicate@example.com", "password123")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.title").value("Conflict"))
			.andExpect(jsonPath("$.detail").value("Email already registered"));
	}

	@Test
	void registerRejectsInvalidPayload() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content(json(new RegisterRequest("", "not-an-email", "short"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name").isNotEmpty())
			.andExpect(jsonPath("$.errors.email").isNotEmpty())
			.andExpect(jsonPath("$.errors.password").isNotEmpty());
	}

	@Test
	void loginSucceedsWithValidCredentials() throws Exception {
		register("login@example.com", "password123");

		mockMvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(json(new LoginRequest("login@example.com", "password123"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.refreshToken").isNotEmpty());
	}

	@Test
	void loginFailsWithWrongPassword() throws Exception {
		register("wrong@example.com", "password123");

		mockMvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(json(new LoginRequest("wrong@example.com", "not-the-password"))))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid email or password"));
	}

	@Test
	void refreshRotatesToken() throws Exception {
		AuthResponse tokens = register("rotate@example.com", "password123");

		AuthResponse rotated = readAuthResponse(refreshRequest(tokens.refreshToken()).andExpect(status().isOk())
			.andReturn());
		assertThat(rotated.refreshToken()).isNotEqualTo(tokens.refreshToken());

		refreshRequest(tokens.refreshToken()).andExpect(status().isUnauthorized());

		refreshRequest(rotated.refreshToken()).andExpect(status().isOk());
	}

	@Test
	void protectedEndpointRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/actuator/info"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Unauthorized"));
	}

	@Test
	void protectedEndpointRejectsInvalidToken() throws Exception {
		mockMvc.perform(get("/actuator/info").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void protectedEndpointAcceptsAccessToken() throws Exception {
		AuthResponse tokens = register("access@example.com", "password123");

		mockMvc.perform(get("/actuator/info").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
			.andExpect(status().isOk());
	}

	@Test
	void oauthLoginCreatesUserAndLinksExistingAccountByEmail() {
		authService.register(new RegisterRequest("Local User", "link@example.com", "password123"));

		authService.loginWithOAuth(Map.of("email", "LINK@example.com", "name", "Google User"));

		assertThat(userRepository.findAll()).hasSize(1);
		User linked = userRepository.findByEmail("link@example.com").orElseThrow();
		assertThat(linked.getProvider()).isEqualTo(UserProvider.LOCAL);

		authService.loginWithOAuth(Map.of("email", "oauth@example.com", "name", "OAuth User"));

		User google = userRepository.findByEmail("oauth@example.com").orElseThrow();
		assertThat(google.getProvider()).isEqualTo(UserProvider.GOOGLE);
		assertThat(google.getPassword()).isNull();
		assertThat(google.getName()).isEqualTo("OAuth User");
	}

	private AuthResponse register(String email, String password) throws Exception {
		MvcResult result = registerRequest(email, password)
			.andExpect(status().isCreated())
			.andReturn();
		return readAuthResponse(result);
	}

	private ResultActions registerRequest(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content(json(new RegisterRequest("Jorge", email, password))));
	}

	private ResultActions refreshRequest(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.content(json(new RefreshRequest(refreshToken))));
	}

	private AuthResponse readAuthResponse(MvcResult result) throws Exception {
		return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
	}

	private String json(Object value) {
		return objectMapper.writeValueAsString(value);
	}

}
