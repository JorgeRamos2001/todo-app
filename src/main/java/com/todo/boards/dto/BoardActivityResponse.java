package com.todo.boards.dto;

import java.time.Instant;
import java.util.Map;

import com.todo.boards.domain.BoardActivity;
import com.todo.boards.domain.BoardActivityAction;
import com.todo.boards.domain.BoardActivityEntityType;

public record BoardActivityResponse(Long id, BoardActivityAction action, BoardActivityEntityType entityType,
		Long entityId, Long actorId, String actorName, Map<String, Object> details, Instant createdAt) {

	public static BoardActivityResponse from(BoardActivity activity) {
		return new BoardActivityResponse(activity.getId(), activity.getAction(), activity.getEntityType(),
				activity.getEntityId(), activity.getActor().getId(), activity.getActor().getName(),
				activity.getDetails(), activity.getCreatedAt());
	}

}
