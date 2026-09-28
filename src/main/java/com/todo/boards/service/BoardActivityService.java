package com.todo.boards.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.todo.boards.domain.Board;
import com.todo.boards.domain.BoardAction;
import com.todo.boards.domain.BoardActivity;
import com.todo.boards.domain.BoardMember;
import com.todo.boards.dto.BoardActivityResponse;
import com.todo.boards.event.BoardActivityEvent;
import com.todo.boards.repository.BoardActivityRepository;
import com.todo.boards.repository.BoardRepository;
import com.todo.shared.dto.PageResponse;
import com.todo.users.domain.User;
import com.todo.users.repository.UserRepository;

@Service
public class BoardActivityService {

	private static final int MAX_PAGE_SIZE = 100;

	private final BoardActivityRepository boardActivityRepository;

	private final BoardRepository boardRepository;

	private final UserRepository userRepository;

	private final BoardPermissionService boardPermissionService;

	public BoardActivityService(BoardActivityRepository boardActivityRepository, BoardRepository boardRepository,
			UserRepository userRepository, BoardPermissionService boardPermissionService) {
		this.boardActivityRepository = boardActivityRepository;
		this.boardRepository = boardRepository;
		this.userRepository = userRepository;
		this.boardPermissionService = boardPermissionService;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(BoardActivityEvent event) {
		Board board = boardRepository.getReferenceById(event.boardId());
		User actor = userRepository.getReferenceById(event.actorId());
		boardActivityRepository.save(new BoardActivity(board, actor, event.action(), event.entityType(),
				event.entityId(), event.details()));
	}

	@Transactional(readOnly = true)
	public PageResponse<BoardActivityResponse> list(Long boardId, Long userId, int page, int size) {
		BoardMember member = boardPermissionService.requireMembership(boardId, userId);
		boardPermissionService.check(member, BoardAction.VIEW_ACTIVITIES);
		Pageable pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE),
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
		return PageResponse.from(boardActivityRepository.findByBoardId(boardId, pageable)
			.map(BoardActivityResponse::from));
	}

}
