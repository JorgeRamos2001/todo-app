package com.todo.tasks.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.todo.auth.security.AuthenticatedUser;
import com.todo.tasks.dto.CreateSubtaskRequest;
import com.todo.tasks.dto.SubtaskResponse;
import com.todo.tasks.dto.UpdateSubtaskRequest;
import com.todo.tasks.service.SubtaskService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Subtasks", description = "Subtareas de una tarea")
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/subtasks")
public class SubtaskController {

	private final SubtaskService subtaskService;

	public SubtaskController(SubtaskService subtaskService) {
		this.subtaskService = subtaskService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	SubtaskResponse create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long taskId,
			@Valid @RequestBody CreateSubtaskRequest request) {
		return subtaskService.create(taskId, user.id(), request);
	}

	@GetMapping
	List<SubtaskResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long taskId) {
		return subtaskService.list(taskId, user.id());
	}

	@PatchMapping("/{subtaskId}")
	SubtaskResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long taskId,
			@PathVariable Long subtaskId, @Valid @RequestBody UpdateSubtaskRequest request) {
		return subtaskService.update(taskId, subtaskId, user.id(), request);
	}

	@DeleteMapping("/{subtaskId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long taskId,
			@PathVariable Long subtaskId) {
		subtaskService.delete(taskId, subtaskId, user.id());
	}

}
