package com.todo.auth.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.todo.auth.dto.AuthResponse;
import com.todo.auth.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

	private final AuthService authService;

	private final OAuth2Properties properties;

	public OAuth2LoginSuccessHandler(AuthService authService, OAuth2Properties properties) {
		this.authService = authService;
		this.properties = properties;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
		AuthResponse tokens = authService.loginWithOAuth(oauthToken.getPrincipal().getAttributes());
		String location = UriComponentsBuilder.fromUriString(properties.redirectUri())
			.queryParam("accessToken", tokens.accessToken())
			.queryParam("refreshToken", tokens.refreshToken())
			.build()
			.toUriString();
		getRedirectStrategy().sendRedirect(request, response, location);
	}

}
