package com.todo.columns.controller;

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
import com.todo.columns.dto.ColumnResponse;
import com.todo.columns.dto.CreateColumnRequest;
import com.todo.columns.dto.UpdateColumnRequest;
import com.todo.columns.service.ColumnService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/boards/{boardId}/columns")
public class ColumnController {

	private final ColumnService columnService;

	public ColumnController(ColumnService columnService) {
		this.columnService = columnService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ColumnResponse create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long boardId,
			@Valid @RequestBody CreateColumnRequest request) {
		return columnService.create(boardId, user.id(), request);
	}

	@GetMapping
	List<ColumnResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long boardId) {
		return columnService.listByBoard(boardId, user.id());
	}

	@PatchMapping("/{columnId}")
	ColumnResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long boardId,
			@PathVariable Long columnId, @Valid @RequestBody UpdateColumnRequest request) {
		return columnService.update(boardId, columnId, user.id(), request);
	}

	@DeleteMapping("/{columnId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long boardId,
			@PathVariable Long columnId) {
		columnService.delete(boardId, columnId, user.id());
	}

}
