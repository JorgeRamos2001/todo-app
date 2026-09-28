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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.todo.auth.security.AuthenticatedUser;
import com.todo.tasks.dto.AssignTaskRequest;
import com.todo.tasks.dto.CreateTaskRequest;
import com.todo.tasks.dto.TaskResponse;
import com.todo.tasks.dto.UpdateTaskRequest;
import com.todo.tasks.service.TaskService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Tasks", description = "Tareas: crear, editar, mover y asignar")
@RestController
public class TaskController {

	private final TaskService taskService;

	public TaskController(TaskService taskService) {
		this.taskService = taskService;
	}

	@PostMapping("/api/v1/columns/{columnId}/tasks")
	@ResponseStatus(HttpStatus.CREATED)
	TaskResponse create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long columnId,
			@Valid @RequestBody CreateTaskRequest request) {
		return taskService.create(columnId, user.id(), request);
	}

	@GetMapping("/api/v1/columns/{columnId}/tasks")
	List<TaskResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long columnId) {
		return taskService.listByColumn(columnId, user.id());
	}

	@PatchMapping("/api/v1/tasks/{taskId}")
	TaskResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long taskId,
			@Valid @RequestBody UpdateTaskRequest request) {
		return taskService.update(taskId, user.id(), request);
	}

	@PostMapping("/api/v1/tasks/{taskId}/assignee")
	TaskResponse assign(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long taskId,
			@Valid @RequestBody AssignTaskRequest request) {
		return taskService.assign(taskId, user.id(), request);
	}

	@DeleteMapping("/api/v1/tasks/{taskId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long taskId) {
		taskService.delete(taskId, user.id());
	}

}
