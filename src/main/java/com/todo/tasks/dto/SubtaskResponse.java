package com.todo.tasks.dto;

import com.todo.tasks.domain.Subtask;

public record SubtaskResponse(Long id, Long taskId, String title, boolean done, Integer position) {

	public static SubtaskResponse from(Subtask subtask) {
		return new SubtaskResponse(subtask.getId(), subtask.getTask().getId(), subtask.getTitle(), subtask.isDone(),
				subtask.getPosition());
	}

}
