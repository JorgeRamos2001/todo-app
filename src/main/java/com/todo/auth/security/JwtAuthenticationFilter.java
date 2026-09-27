package com.todo.auth.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.todo.users.domain.User;
import com.todo.users.repository.UserRepository;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;

	private final UserRepository userRepository;

	public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
			authenticate(authorization.substring(BEARER_PREFIX.length()), request);
		}
		filterChain.doFilter(request, response);
	}

	private void authenticate(String token, HttpServletRequest request) {
		try {
			Long userId = jwtService.extractUserId(token);
			userRepository.findById(userId).ifPresent((user) -> setAuthentication(user, request));
		}
		catch (JwtException | IllegalArgumentException exception) {
			SecurityContextHolder.clearContext();
		}
	}

	private void setAuthentication(User user, HttpServletRequest request) {
		AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getName());
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, null,
				List.of());
		authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

}
