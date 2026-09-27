package com.todo.auth.dto;

import java.time.Duration;

public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

	public static AuthResponse bearer(String accessToken, String refreshToken, Duration accessTokenTtl) {
		return new AuthResponse(accessToken, refreshToken, "Bearer", accessTokenTtl.toSeconds());
	}

}
