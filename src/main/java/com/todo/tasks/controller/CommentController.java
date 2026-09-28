package com.todo.tasks.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.todo.auth.security.AuthenticatedUser;
import com.todo.tasks.dto.CommentResponse;
import com.todo.tasks.dto.CreateCommentRequest;
import com.todo.tasks.service.CommentService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Comments", description = "Comentarios de una tarea")
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/comments")
public class CommentController {

	private final CommentService commentService;

	public CommentController(CommentService commentService) {
		this.commentService = commentService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	CommentResponse create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long taskId,
			@Valid @RequestBody CreateCommentRequest request) {
		return commentService.create(taskId, user.id(), request);
	}

	@GetMapping
	List<CommentResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long taskId) {
		return commentService.list(taskId, user.id());
	}

	@DeleteMapping("/{commentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long taskId,
			@PathVariable Long commentId) {
		commentService.delete(taskId, commentId, user.id());
	}

}
