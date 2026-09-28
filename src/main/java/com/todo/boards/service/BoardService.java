package com.todo.boards.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.todo.boards.domain.Board;
import com.todo.boards.domain.BoardAction;
import com.todo.boards.domain.BoardActivityAction;
import com.todo.boards.domain.BoardActivityEntityType;
import com.todo.boards.domain.BoardMember;
import com.todo.boards.domain.BoardRole;
import com.todo.boards.dto.BoardDetailResponse;
import com.todo.boards.dto.BoardMemberResponse;
import com.todo.boards.dto.BoardSummaryResponse;
import com.todo.boards.dto.CreateBoardRequest;
import com.todo.boards.dto.UpdateBoardRequest;
import com.todo.boards.event.BoardEventPublisher;
import com.todo.boards.repository.BoardMemberRepository;
import com.todo.boards.repository.BoardRepository;
import com.todo.columns.dto.ColumnResponse;
import com.todo.columns.service.ColumnService;
import com.todo.shared.error.BadRequestException;
import com.todo.shared.error.ResourceNotFoundException;
import com.todo.users.domain.User;
import com.todo.users.repository.UserRepository;

@Service
public class BoardService {

	private final BoardRepository boardRepository;

	private final BoardMemberRepository boardMemberRepository;

	private final BoardPermissionService boardPermissionService;

	private final ColumnService columnService;

	private final BoardEventPublisher boardEventPublisher;

	private final UserRepository userRepository;

	public BoardService(BoardRepository boardRepository, BoardMemberRepository boardMemberRepository,
			BoardPermissionService boardPermissionService, ColumnService columnService,
			BoardEventPublisher boardEventPublisher, UserRepository userRepository) {
		this.boardRepository = boardRepository;
		this.boardMemberRepository = boardMemberRepository;
		this.boardPermissionService = boardPermissionService;
		this.columnService = columnService;
		this.boardEventPublisher = boardEventPublisher;
		this.userRepository = userRepository;
	}

	@Transactional
	public BoardDetailResponse create(Long userId, CreateBoardRequest request) {
		User owner = userRepository.findById(userId)
			.orElseThrow(() -> new ResourceNotFoundException("User not found"));
		Board board = boardRepository
			.save(new Board(request.title().trim(), request.description(), request.type(), owner));
		BoardMember ownerMembership = boardMemberRepository.save(new BoardMember(board, owner, BoardRole.OWNER));
		boardEventPublisher.publish(board.getId(), userId, BoardActivityAction.BOARD_CREATED,
				BoardActivityEntityType.BOARD, board.getId(),
				Map.of("title", board.getTitle(), "type", board.getType().name()));
		return toDetail(board, List.of(ownerMembership), List.of());
	}

	@Transactional(readOnly = true)
	public List<BoardSummaryResponse> listMine(Long userId) {
		return boardMemberRepository.findByUserId(userId)
			.stream()
			.sorted(Comparator.comparing((BoardMember member) -> member.getBoard().getId()).reversed())
			.map((member) -> toSummary(member.getBoard(), member.getRole()))
			.toList();
	}

	@Transactional(readOnly = true)
	public BoardDetailResponse getDetail(Long boardId, Long userId) {
		BoardMember member = boardPermissionService.requireMembership(boardId, userId);
		boardPermissionService.check(member, BoardAction.VIEW_BOARD);
		return toDetail(member.getBoard(), boardMemberRepository.findByBoardId(boardId),
				columnService.listByBoard(boardId, userId));
	}

	@Transactional
	public BoardDetailResponse update(Long boardId, Long userId, UpdateBoardRequest request) {
		BoardMember member = boardPermissionService.requireMembership(boardId, userId);
		boardPermissionService.check(member, BoardAction.EDIT_BOARD);
		String title = request.title() == null ? null : request.title().trim();
		if (title != null && title.isEmpty()) {
			throw new BadRequestException("Title must not be blank");
		}
		Board board = member.getBoard();
		board.updateDetails(title, request.description());
		boardEventPublisher.publish(boardId, userId, BoardActivityAction.BOARD_UPDATED,
				BoardActivityEntityType.BOARD, boardId, Map.of("title", board.getTitle()));
		return toDetail(board, boardMemberRepository.findByBoardId(boardId), columnService.listByBoard(boardId, userId));
	}

	@Transactional
	public void delete(Long boardId, Long userId) {
		BoardMember member = boardPermissionService.requireMembership(boardId, userId);
		boardPermissionService.check(member, BoardAction.DELETE_BOARD);
		boardRepository.delete(member.getBoard());
	}

	@Transactional(readOnly = true)
	public List<BoardMemberResponse> listMembers(Long boardId, Long userId) {
		BoardMember member = boardPermissionService.requireMembership(boardId, userId);
		boardPermissionService.check(member, BoardAction.VIEW_BOARD);
		return boardMemberRepository.findByBoardId(boardId).stream().map(this::toMember).toList();
	}

	@Transactional
	public void removeMember(Long boardId, Long actorId, Long memberId) {
		BoardMember actor = boardPermissionService.requireMembership(boardId, actorId);
		BoardMember target = boardMemberRepository.findById(memberId)
			.filter((candidate) -> candidate.getBoard().getId().equals(boardId))
			.orElseThrow(() -> new ResourceNotFoundException("Board member not found"));
		boardPermissionService.checkRemoveMember(actor, target);
		boardMemberRepository.delete(target);
		boardEventPublisher.publish(boardId, actorId, BoardActivityAction.MEMBER_REMOVED,
				BoardActivityEntityType.MEMBER, target.getUser().getId(),
				Map.of("email", target.getUser().getEmail(), "role", target.getRole().name()));
	}

	private BoardDetailResponse toDetail(Board board, List<BoardMember> members, List<ColumnResponse> columns) {
		return new BoardDetailResponse(board.getId(), board.getTitle(), board.getDescription(), board.getType(),
				board.getOwner().getId(), members.stream().map(this::toMember).toList(), columns);
	}

	private BoardSummaryResponse toSummary(Board board, BoardRole role) {
		return new BoardSummaryResponse(board.getId(), board.getTitle(), board.getType(), board.getOwner().getId(),
				role);
	}

	private BoardMemberResponse toMember(BoardMember member) {
		User user = member.getUser();
		return new BoardMemberResponse(member.getId(), user.getId(), user.getName(), user.getEmail(), member.getRole());
	}

}
