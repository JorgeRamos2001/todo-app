package com.todo.boards.event;

import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.todo.boards.domain.BoardActivityAction;
import com.todo.boards.domain.BoardActivityEntityType;

@Component
public class BoardEventPublisher {

	private final ApplicationEventPublisher applicationEventPublisher;

	public BoardEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	public void publish(Long boardId, Long actorId, BoardActivityAction action, BoardActivityEntityType entityType,
			Long entityId, Map<String, Object> details) {
		applicationEventPublisher
			.publishEvent(new BoardActivityEvent(boardId, actorId, action, entityType, entityId, details));
	}

}
