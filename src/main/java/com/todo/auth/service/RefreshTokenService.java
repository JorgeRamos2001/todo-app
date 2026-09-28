package com.todo.auth.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.todo.auth.domain.RefreshToken;
import com.todo.auth.repository.RefreshTokenRepository;
import com.todo.auth.security.JwtProperties;
import com.todo.shared.error.UnauthorizedException;
import com.todo.shared.util.SecureTokens;
import com.todo.users.domain.User;

@Service
public class RefreshTokenService {

	private final RefreshTokenRepository refreshTokenRepository;

	private final JwtProperties jwtProperties;

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.jwtProperties = jwtProperties;
	}

	@Transactional
	public String issue(User user) {
		String token = SecureTokens.randomToken();
		RefreshToken refreshToken = new RefreshToken(user, SecureTokens.sha256Hex(token),
				Instant.now().plus(jwtProperties.refreshTokenTtl()));
		refreshTokenRepository.save(refreshToken);
		return token;
	}

	@Transactional
	public User consume(String token) {
		RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(SecureTokens.sha256Hex(token))
			.orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
		if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(Instant.now())) {
			throw new UnauthorizedException("Refresh token expired or revoked");
		}
		refreshToken.revoke();
		return refreshToken.getUser();
	}

}
