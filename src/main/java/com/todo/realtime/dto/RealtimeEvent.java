package com.todo.realtime.dto;

import java.time.Instant;
import java.util.Map;

import com.todo.boards.domain.BoardActivityAction;
import com.todo.boards.domain.BoardActivityEntityType;

public record RealtimeEvent(BoardActivityAction type, Long boardId, BoardActivityEntityType entityType, Long entityId,
		Long actorId, Map<String, Object> payload, Instant occurredAt) {

}
