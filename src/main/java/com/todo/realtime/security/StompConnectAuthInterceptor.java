package com.todo.realtime.security;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.todo.auth.security.JwtService;
import com.todo.users.domain.User;
import com.todo.users.repository.UserRepository;

import io.jsonwebtoken.JwtException;

@Component
public class StompConnectAuthInterceptor implements ChannelInterceptor {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;

	private final UserRepository userRepository;

	public StompConnectAuthInterceptor(JwtService jwtService, UserRepository userRepository) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
			accessor.setUser(authenticate(accessor));
		}
		return message;
	}

	private RealtimePrincipal authenticate(StompHeaderAccessor accessor) {
		List<String> authorization = accessor.getNativeHeader(HttpHeaders.AUTHORIZATION);
		if (authorization == null || authorization.isEmpty() || !authorization.getFirst().startsWith(BEARER_PREFIX)) {
			throw new MessagingException("Missing Authorization header on STOMP CONNECT");
		}
		String token = authorization.getFirst().substring(BEARER_PREFIX.length());
		try {
			Long userId = jwtService.extractUserId(token);
			User user = userRepository.findById(userId)
				.orElseThrow(() -> new MessagingException("Unknown user in STOMP CONNECT token"));
			return new RealtimePrincipal(user.getId(), user.getEmail(), user.getName());
		}
		catch (JwtException | IllegalArgumentException exception) {
			throw new MessagingException("Invalid JWT on STOMP CONNECT");
		}
	}

}
