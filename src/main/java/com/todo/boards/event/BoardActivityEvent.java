package com.todo.boards.event;

import java.util.Map;

import com.todo.boards.domain.BoardActivityAction;
import com.todo.boards.domain.BoardActivityEntityType;

public record BoardActivityEvent(Long boardId, Long actorId, BoardActivityAction action,
		BoardActivityEntityType entityType, Long entityId, Map<String, Object> details) {

	public BoardActivityEvent {
		details = details == null ? Map.of() : Map.copyOf(details);
	}

}
