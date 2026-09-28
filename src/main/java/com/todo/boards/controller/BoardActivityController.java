package com.todo.boards.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.todo.auth.security.AuthenticatedUser;
import com.todo.boards.dto.BoardActivityResponse;
import com.todo.boards.service.BoardActivityService;
import com.todo.shared.dto.PageResponse;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Board activities", description = "Log de actividad del tablero (solo Owner)")
@RestController
@RequestMapping("/api/v1/boards/{boardId}/activities")
public class BoardActivityController {

	private final BoardActivityService boardActivityService;

	public BoardActivityController(BoardActivityService boardActivityService) {
		this.boardActivityService = boardActivityService;
	}

	@GetMapping
	PageResponse<BoardActivityResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable Long boardId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return boardActivityService.list(boardId, user.id(), page, size);
	}

}
