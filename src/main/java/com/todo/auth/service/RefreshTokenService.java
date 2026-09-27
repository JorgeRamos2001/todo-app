package com.todo.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.todo.auth.domain.RefreshToken;
import com.todo.auth.repository.RefreshTokenRepository;
import com.todo.auth.security.JwtProperties;
import com.todo.shared.error.UnauthorizedException;
import com.todo.users.domain.User;

@Service
public class RefreshTokenService {

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();

	private final RefreshTokenRepository refreshTokenRepository;

	private final JwtProperties jwtProperties;

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.jwtProperties = jwtProperties;
	}

	@Transactional
	public String issue(User user) {
		String token = generateToken();
		RefreshToken refreshToken = new RefreshToken(user, hash(token),
				Instant.now().plus(jwtProperties.refreshTokenTtl()));
		refreshTokenRepository.save(refreshToken);
		return token;
	}

	@Transactional
	public User consume(String token) {
		RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash(token))
			.orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
		if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(Instant.now())) {
			throw new UnauthorizedException("Refresh token expired or revoked");
		}
		refreshToken.revoke();
		return refreshToken.getUser();
	}

	private String generateToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 algorithm not available", exception);
		}
	}

}
