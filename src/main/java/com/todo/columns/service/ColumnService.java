package com.todo.columns.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.todo.boards.domain.BoardAction;
import com.todo.boards.domain.BoardActivityAction;
import com.todo.boards.domain.BoardActivityEntityType;
import com.todo.boards.domain.BoardMember;
import com.todo.boards.event.BoardEventPublisher;
import com.todo.boards.service.BoardPermissionService;
import com.todo.columns.domain.BoardColumn;
import com.todo.columns.dto.ColumnResponse;
import com.todo.columns.dto.CreateColumnRequest;
import com.todo.columns.dto.UpdateColumnRequest;
import com.todo.columns.repository.BoardColumnRepository;
import com.todo.shared.error.BadRequestException;
import com.todo.shared.error.ResourceNotFoundException;
import com.todo.tasks.domain.Task;
import com.todo.tasks.dto.TaskResponse;
import com.todo.tasks.repository.TaskRepository;

@Service
public class ColumnService {

	private final BoardColumnRepository boardColumnRepository;

	private final TaskRepository taskRepository;

	private final BoardPermissionService boardPermissionService;

	private final BoardEventPublisher boardEventPublisher;

	public ColumnService(BoardColumnRepository boardColumnRepository, TaskRepository taskRepository,
			BoardPermissionService boardPermissionService, BoardEventPublisher boardEventPublisher) {
		this.boardColumnRepository = boardColumnRepository;
		this.taskRepository = taskRepository;
		this.boardPermissionService = boardPermissionService;
		this.boardEventPublisher = boardEventPublisher;
	}

	@Transactional
	public ColumnResponse create(Long boardId, Long userId, CreateColumnRequest request) {
		BoardMember actor = boardPermissionService.requireMembership(boardId, userId);
		boardPermissionService.check(actor, BoardAction.MANAGE_COLUMNS);
		int position = boardColumnRepository.findByBoardIdOrderByPosition(boardId).size();
		BoardColumn column = boardColumnRepository
			.save(new BoardColumn(actor.getBoard(), request.name().trim(), position));
		boardEventPublisher.publish(boardId, userId, BoardActivityAction.COLUMN_CREATED,
				BoardActivityEntityType.COLUMN, column.getId(), Map.of("name", column.getName()));
		return toResponse(column, List.of());
	}

	@Transactional(readOnly = true)
	public List<ColumnResponse> listByBoard(Long boardId, Long userId) {
		BoardMember actor = boardPermissionService.requireMembership(boardId, userId);
		boardPermissionService.check(actor, BoardAction.VIEW_BOARD);
		List<BoardColumn> columns = boardColumnRepository.findByBoardIdOrderByPosition(boardId);
		Map<Long, List<Task>> tasksByColumn = tasksByColumn(columns);
		return columns.stream()
			.map((column) -> toResponse(column, tasksByColumn.getOrDefault(column.getId(), List.of())))
			.toList();
	}

	@Transactional
	public ColumnResponse update(Long boardId, Long columnId, Long userId, UpdateColumnRequest request) {
		BoardMember actor = boardPermissionService.requireMembership(boardId, userId);
		boardPermissionService.check(actor, BoardAction.MANAGE_COLUMNS);
		BoardColumn column = findColumn(boardId, columnId);
		if (request.name() != null) {
			String name = request.name().trim();
			if (name.isEmpty()) {
				throw new BadRequestException("Column name must not be blank");
			}
			column.rename(name);
		}
		if (request.position() != null) {
			placeColumn(column, request.position());
		}
		boardEventPublisher.publish(boardId, userId, BoardActivityAction.COLUMN_UPDATED,
				BoardActivityEntityType.COLUMN, columnId,
				Map.of("name", column.getName(), "position", column.getPosition()));
		return toResponse(column, taskRepository.findByColumnIdOrderByPosition(columnId));
	}

	@Transactional
	public void delete(Long boardId, Long columnId, Long userId) {
		BoardMember actor = boardPermissionService.requireMembership(boardId, userId);
		boardPermissionService.check(actor, BoardAction.MANAGE_COLUMNS);
		BoardColumn column = findColumn(boardId, columnId);
		boardColumnRepository.delete(column);
		reindex(boardId);
		boardEventPublisher.publish(boardId, userId, BoardActivityAction.COLUMN_DELETED,
				BoardActivityEntityType.COLUMN, columnId, Map.of("name", column.getName()));
	}

	private BoardColumn findColumn(Long boardId, Long columnId) {
		return boardColumnRepository.findByIdAndBoardId(columnId, boardId)
			.orElseThrow(() -> new ResourceNotFoundException("Column not found"));
	}

	private void placeColumn(BoardColumn column, int position) {
		List<BoardColumn> columns = boardColumnRepository.findByBoardIdOrderByPosition(column.getBoard().getId());
		columns.remove(column);
		int index = Math.max(0, Math.min(position, columns.size()));
		columns.add(index, column);
		for (int i = 0; i < columns.size(); i++) {
			columns.get(i).changePosition(i);
		}
	}

	private void reindex(Long boardId) {
		List<BoardColumn> columns = boardColumnRepository.findByBoardIdOrderByPosition(boardId);
		for (int i = 0; i < columns.size(); i++) {
			columns.get(i).changePosition(i);
		}
	}

	private Map<Long, List<Task>> tasksByColumn(List<BoardColumn> columns) {
		List<Long> columnIds = columns.stream().map(BoardColumn::getId).toList();
		if (columnIds.isEmpty()) {
			return Map.of();
		}
		return taskRepository.findByColumnIdInOrderByPosition(columnIds)
			.stream()
			.collect(Collectors.groupingBy((task) -> task.getColumn().getId(), LinkedHashMap::new,
					Collectors.toList()));
	}

	private ColumnResponse toResponse(BoardColumn column, List<Task> tasks) {
		return new ColumnResponse(column.getId(), column.getName(), column.getPosition(),
				tasks.stream().map(TaskResponse::from).toList());
	}

}
