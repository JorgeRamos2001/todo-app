package com.todo.tasks.dto;

import java.time.Instant;

import com.todo.tasks.domain.Comment;

public record CommentResponse(Long id, Long taskId, Long authorId, String authorName, String content,
		Instant createdAt) {

	public static CommentResponse from(Comment comment) {
		return new CommentResponse(comment.getId(), comment.getTask().getId(), comment.getAuthor().getId(),
				comment.getAuthor().getName(), comment.getContent(), comment.getCreatedAt());
	}

}
