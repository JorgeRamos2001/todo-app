package com.todo.tasks.dto;

import java.time.Instant;

import com.todo.tasks.domain.Task;
import com.todo.users.domain.User;

public record TaskResponse(Long id, Long columnId, String title, String description, Integer position, Long assigneeId,
		String assigneeName, Long createdById, Instant createdAt, Instant updatedAt) {

	public static TaskResponse from(Task task) {
		User assignee = task.getAssignee();
		return new TaskResponse(task.getId(), task.getColumn().getId(), task.getTitle(), task.getDescription(),
				task.getPosition(), assignee == null ? null : assignee.getId(),
				assignee == null ? null : assignee.getName(), task.getCreatedBy().getId(), task.getCreatedAt(),
				task.getUpdatedAt());
	}

}
