package com.todo.tasks.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.todo.boards.domain.BoardAction;
import com.todo.boards.domain.BoardActivityAction;
import com.todo.boards.domain.BoardActivityEntityType;
import com.todo.boards.domain.BoardMember;
import com.todo.boards.event.BoardEventPublisher;
import com.todo.boards.repository.BoardMemberRepository;
import com.todo.boards.service.BoardPermissionService;
import com.todo.columns.domain.BoardColumn;
import com.todo.columns.repository.BoardColumnRepository;
import com.todo.shared.error.BadRequestException;
import com.todo.shared.error.ResourceNotFoundException;
import com.todo.tasks.domain.Task;
import com.todo.tasks.dto.AssignTaskRequest;
import com.todo.tasks.dto.CreateTaskRequest;
import com.todo.tasks.dto.TaskResponse;
import com.todo.tasks.dto.UpdateTaskRequest;
import com.todo.tasks.repository.TaskRepository;

@Service
public class TaskService {

	private final TaskRepository taskRepository;

	private final BoardColumnRepository boardColumnRepository;

	private final BoardMemberRepository boardMemberRepository;

	private final BoardPermissionService boardPermissionService;

	private final BoardEventPublisher boardEventPublisher;

	public TaskService(TaskRepository taskRepository, BoardColumnRepository boardColumnRepository,
			BoardMemberRepository boardMemberRepository, BoardPermissionService boardPermissionService,
			BoardEventPublisher boardEventPublisher) {
		this.taskRepository = taskRepository;
		this.boardColumnRepository = boardColumnRepository;
		this.boardMemberRepository = boardMemberRepository;
		this.boardPermissionService = boardPermissionService;
		this.boardEventPublisher = boardEventPublisher;
	}

	@Transactional
	public TaskResponse create(Long columnId, Long userId, CreateTaskRequest request) {
		BoardColumn column = findColumn(columnId);
		BoardMember actor = boardPermissionService.requireMembership(column.getBoard().getId(), userId);
		boardPermissionService.check(actor, BoardAction.CREATE_TASK);
		int position = taskRepository.findByColumnIdOrderByPosition(columnId).size();
		Task task = taskRepository
			.save(new Task(column, request.title().trim(), request.description(), position, actor.getUser()));
		boardEventPublisher.publish(column.getBoard().getId(), userId, BoardActivityAction.TASK_CREATED,
				BoardActivityEntityType.TASK, task.getId(),
				Map.of("title", task.getTitle(), "columnId", columnId));
		return TaskResponse.from(task);
	}

	@Transactional(readOnly = true)
	public List<TaskResponse> listByColumn(Long columnId, Long userId) {
		BoardColumn column = findColumn(columnId);
		BoardMember actor = boardPermissionService.requireMembership(column.getBoard().getId(), userId);
		boardPermissionService.check(actor, BoardAction.VIEW_BOARD);
		return taskRepository.findByColumnIdOrderByPosition(columnId).stream().map(TaskResponse::from).toList();
	}

	@Transactional
	public TaskResponse update(Long taskId, Long userId, UpdateTaskRequest request) {
		Task task = findTask(taskId);
		Long boardId = task.getColumn().getBoard().getId();
		BoardMember actor = boardPermissionService.requireMembership(boardId, userId);
		Long assigneeId = assigneeId(task);
		boardPermissionService.checkEditTask(actor, assigneeId);
		boolean changingColumn = request.columnId() != null && !request.columnId().equals(task.getColumn().getId());
		boolean moving = changingColumn || request.position() != null;
		if (moving) {
			boardPermissionService.checkMoveTask(actor, assigneeId);
		}
		String title = request.title() == null ? null : request.title().trim();
		if (title != null && title.isEmpty()) {
			throw new BadRequestException("Task title must not be blank");
		}
		Long sourceColumnId = task.getColumn().getId();
		task.updateDetails(title, request.description());
		if (moving) {
			BoardColumn target = changingColumn ? findColumn(request.columnId()) : task.getColumn();
			if (!target.getBoard().getId().equals(boardId)) {
				throw new BadRequestException("Task can only be moved within the same board");
			}
			placeTask(task, target, request.position());
			boardEventPublisher.publish(boardId, userId, BoardActivityAction.TASK_MOVED,
					BoardActivityEntityType.TASK, taskId,
					Map.of("title", task.getTitle(), "fromColumnId", sourceColumnId, "toColumnId",
							task.getColumn().getId(), "position", task.getPosition()));
		}
		else {
			boardEventPublisher.publish(boardId, userId, BoardActivityAction.TASK_UPDATED,
					BoardActivityEntityType.TASK, taskId, Map.of("title", task.getTitle()));
		}
		return TaskResponse.from(task);
	}

	@Transactional
	public TaskResponse assign(Long taskId, Long userId, AssignTaskRequest request) {
		Task task = findTask(taskId);
		Long boardId = task.getColumn().getBoard().getId();
		BoardMember actor = boardPermissionService.requireMembership(boardId, userId);
		boardPermissionService.check(actor, BoardAction.ASSIGN_TASK);
		BoardMember assignee = boardMemberRepository.findByBoardIdAndUserId(boardId, request.userId())
			.orElseThrow(() -> new BadRequestException("Assignee must be a board member"));
		task.assign(assignee.getUser());
		boardEventPublisher.publish(boardId, userId, BoardActivityAction.TASK_ASSIGNED,
				BoardActivityEntityType.TASK, taskId,
				Map.of("title", task.getTitle(), "assigneeId", assignee.getUser().getId(), "assigneeName",
						assignee.getUser().getName()));
		return TaskResponse.from(task);
	}

	@Transactional
	public void delete(Long taskId, Long userId) {
		Task task = findTask(taskId);
		Long boardId = task.getColumn().getBoard().getId();
		Long columnId = task.getColumn().getId();
		BoardMember actor = boardPermissionService.requireMembership(boardId, userId);
		boardPermissionService.check(actor, BoardAction.DELETE_TASK);
		boardEventPublisher.publish(boardId, userId, BoardActivityAction.TASK_DELETED,
				BoardActivityEntityType.TASK, taskId, Map.of("title", task.getTitle()));
		taskRepository.delete(task);
		reindex(columnId);
	}

	private void placeTask(Task task, BoardColumn target, Integer position) {
		Long sourceColumnId = task.getColumn().getId();
		if (!sourceColumnId.equals(target.getId())) {
			task.moveTo(target, 0);
		}
		List<Task> tasks = taskRepository.findByColumnIdOrderByPosition(target.getId());
		tasks.remove(task);
		int index = position == null ? tasks.size() : Math.max(0, Math.min(position, tasks.size()));
		tasks.add(index, task);
		for (int i = 0; i < tasks.size(); i++) {
			tasks.get(i).changePosition(i);
		}
		if (!sourceColumnId.equals(target.getId())) {
			reindex(sourceColumnId);
		}
	}

	private void reindex(Long columnId) {
		List<Task> tasks = taskRepository.findByColumnIdOrderByPosition(columnId);
		for (int i = 0; i < tasks.size(); i++) {
			tasks.get(i).changePosition(i);
		}
	}

	private Long assigneeId(Task task) {
		return task.getAssignee() == null ? null : task.getAssignee().getId();
	}

	private BoardColumn findColumn(Long columnId) {
		return boardColumnRepository.findById(columnId)
			.orElseThrow(() -> new ResourceNotFoundException("Column not found"));
	}

	private Task findTask(Long taskId) {
		return taskRepository.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task not found"));
	}

}
