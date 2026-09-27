package com.todo.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.todo.users.domain.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	private static final int MIN_SECRET_BYTES = 32;

	private final SecretKey key;

	private final Duration accessTokenTtl;

	public JwtService(JwtProperties properties) {
		byte[] secret = properties.secret() == null ? new byte[0]
				: properties.secret().getBytes(StandardCharsets.UTF_8);
		if (secret.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("app.security.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes");
		}
		this.key = Keys.hmacShaKeyFor(secret);
		this.accessTokenTtl = properties.accessTokenTtl();
	}

	public String generateAccessToken(User user) {
		Instant now = Instant.now();
		return Jwts.builder()
			.subject(user.getId().toString())
			.claim("email", user.getEmail())
			.claim("name", user.getName())
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(accessTokenTtl)))
			.signWith(key)
			.compact();
	}

	public Long extractUserId(String token) {
		Claims claims = Jwts.parser()
			.verifyWith(key)
			.build()
			.parseSignedClaims(token)
			.getPayload();
		return Long.valueOf(claims.getSubject());
	}

	public Duration accessTokenTtl() {
		return accessTokenTtl;
	}

}
