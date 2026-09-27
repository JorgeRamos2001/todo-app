package com.todo.auth.service;

import java.util.Locale;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.todo.auth.dto.AuthResponse;
import com.todo.auth.dto.LoginRequest;
import com.todo.auth.dto.RegisterRequest;
import com.todo.auth.security.JwtService;
import com.todo.shared.error.ConflictException;
import com.todo.shared.error.UnauthorizedException;
import com.todo.users.domain.User;
import com.todo.users.repository.UserRepository;

@Service
public class AuthService {

	private final UserRepository userRepository;

	private final RefreshTokenService refreshTokenService;

	private final JwtService jwtService;

	private final PasswordEncoder passwordEncoder;

	public AuthService(UserRepository userRepository, RefreshTokenService refreshTokenService, JwtService jwtService,
			PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.refreshTokenService = refreshTokenService;
		this.jwtService = jwtService;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new ConflictException("Email already registered");
		}
		User user = userRepository
			.save(User.local(request.name().trim(), email, passwordEncoder.encode(request.password())));
		return issueTokens(user);
	}

	@Transactional
	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(normalizeEmail(request.email()))
			.filter((candidate) -> candidate.getPassword() != null)
			.filter((candidate) -> passwordEncoder.matches(request.password(), candidate.getPassword()))
			.orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
		return issueTokens(user);
	}

	@Transactional
	public AuthResponse refresh(String refreshToken) {
		User user = refreshTokenService.consume(refreshToken);
		return issueTokens(user);
	}

	@Transactional
	public AuthResponse loginWithOAuth(Map<String, Object> attributes) {
		Object emailAttribute = attributes.get("email");
		if (!(emailAttribute instanceof String emailValue) || emailValue.isBlank()) {
			throw new UnauthorizedException("OAuth provider did not return an email address");
		}
		String email = normalizeEmail(emailValue);
		String name = extractName(attributes, email);
		User user = userRepository.findByEmail(email)
			.orElseGet(() -> userRepository.save(User.google(name, email)));
		return issueTokens(user);
	}

	private AuthResponse issueTokens(User user) {
		String accessToken = jwtService.generateAccessToken(user);
		String refreshToken = refreshTokenService.issue(user);
		return AuthResponse.bearer(accessToken, refreshToken, jwtService.accessTokenTtl());
	}

	private String extractName(Map<String, Object> attributes, String fallback) {
		Object nameAttribute = attributes.get("name");
		if (nameAttribute instanceof String nameValue && !nameValue.isBlank()) {
			return nameValue.trim();
		}
		return fallback;
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
