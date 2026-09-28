package com.todo.realtime.security;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.todo.boards.service.BoardPermissionService;
import com.todo.shared.error.ForbiddenException;

@Component
public class StompSubscribeAuthInterceptor implements ChannelInterceptor {

	private static final Pattern BOARD_TOPIC = Pattern.compile("^/topic/boards/(\\d+)$");

	private final BoardPermissionService boardPermissionService;

	public StompSubscribeAuthInterceptor(BoardPermissionService boardPermissionService) {
		this.boardPermissionService = boardPermissionService;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
			authorize(accessor);
		}
		return message;
	}

	private void authorize(StompHeaderAccessor accessor) {
		String destination = accessor.getDestination();
		if (destination == null) {
			throw new MessagingException("Missing destination on STOMP SUBSCRIBE");
		}
		Matcher matcher = BOARD_TOPIC.matcher(destination);
		if (!matcher.matches()) {
			throw new MessagingException("Subscriptions are only allowed to /topic/boards/{boardId}");
		}
		Principal user = accessor.getUser();
		if (!(user instanceof RealtimePrincipal principal)) {
			throw new MessagingException("STOMP session is not authenticated");
		}
		Long boardId = Long.valueOf(matcher.group(1));
		try {
			boardPermissionService.requireMembership(boardId, principal.id());
		}
		catch (ForbiddenException exception) {
			throw new MessagingException("Not a member of board " + boardId);
		}
	}

}
