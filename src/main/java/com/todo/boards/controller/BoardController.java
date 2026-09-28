package com.todo.boards.controller;

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
import com.todo.boards.dto.BoardDetailResponse;
import com.todo.boards.dto.BoardMemberResponse;
import com.todo.boards.dto.BoardSummaryResponse;
import com.todo.boards.dto.CreateBoardRequest;
import com.todo.boards.dto.UpdateBoardRequest;
import com.todo.boards.service.BoardService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Boards", description = "Tableros, membresias y permisos")
@RestController
@RequestMapping("/api/v1/boards")
public class BoardController {

	private final BoardService boardService;

	public BoardController(BoardService boardService) {
		this.boardService = boardService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	BoardDetailResponse create(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody CreateBoardRequest request) {
		return boardService.create(user.id(), request);
	}

	@GetMapping
	List<BoardSummaryResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
		return boardService.listMine(user.id());
	}

	@GetMapping("/{boardId}")
	BoardDetailResponse detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long boardId) {
		return boardService.getDetail(boardId, user.id());
	}

	@PatchMapping("/{boardId}")
	BoardDetailResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long boardId,
			@Valid @RequestBody UpdateBoardRequest request) {
		return boardService.update(boardId, user.id(), request);
	}

	@DeleteMapping("/{boardId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long boardId) {
		boardService.delete(boardId, user.id());
	}

	@GetMapping("/{boardId}/members")
	List<BoardMemberResponse> members(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long boardId) {
		return boardService.listMembers(boardId, user.id());
	}

	@DeleteMapping("/{boardId}/members/{memberId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeMember(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long boardId,
			@PathVariable Long memberId) {
		boardService.removeMember(boardId, user.id(), memberId);
	}

}
