package com.todo.tasks.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.todo.boards.domain.BoardAction;
import com.todo.boards.domain.BoardMember;
import com.todo.boards.service.BoardPermissionService;
import com.todo.shared.error.BadRequestException;
import com.todo.shared.error.ResourceNotFoundException;
import com.todo.tasks.domain.Subtask;
import com.todo.tasks.domain.Task;
import com.todo.tasks.dto.CreateSubtaskRequest;
import com.todo.tasks.dto.SubtaskResponse;
import com.todo.tasks.dto.UpdateSubtaskRequest;
import com.todo.tasks.repository.SubtaskRepository;
import com.todo.tasks.repository.TaskRepository;

@Service
public class SubtaskService {

	private final SubtaskRepository subtaskRepository;

	private final TaskRepository taskRepository;

	private final BoardPermissionService boardPermissionService;

	public SubtaskService(SubtaskRepository subtaskRepository, TaskRepository taskRepository,
			BoardPermissionService boardPermissionService) {
		this.subtaskRepository = subtaskRepository;
		this.taskRepository = taskRepository;
		this.boardPermissionService = boardPermissionService;
	}

	@Transactional
	public SubtaskResponse create(Long taskId, Long userId, CreateSubtaskRequest request) {
		Task task = findTask(taskId);
		requireManageSubtasks(task, userId);
		int position = subtaskRepository.findByTaskIdOrderByPosition(taskId).size();
		Subtask subtask = subtaskRepository.save(new Subtask(task, request.title().trim(), position));
		return SubtaskResponse.from(subtask);
	}

	@Transactional(readOnly = true)
	public List<SubtaskResponse> list(Long taskId, Long userId) {
		Task task = findTask(taskId);
		requireView(task, userId);
		return subtaskRepository.findByTaskIdOrderByPosition(taskId).stream().map(SubtaskResponse::from).toList();
	}

	@Transactional
	public SubtaskResponse update(Long taskId, Long subtaskId, Long userId, UpdateSubtaskRequest request) {
		Task task = findTask(taskId);
		requireManageSubtasks(task, userId);
		Subtask subtask = findSubtask(taskId, subtaskId);
		if (request.title() != null) {
			String title = request.title().trim();
			if (title.isEmpty()) {
				throw new BadRequestException("Subtask title must not be blank");
			}
			subtask.rename(title);
		}
		if (request.done() != null) {
			subtask.markDone(request.done());
		}
		if (request.position() != null) {
			placeSubtask(subtask, request.position());
		}
		return SubtaskResponse.from(subtask);
	}

	@Transactional
	public void delete(Long taskId, Long subtaskId, Long userId) {
		Task task = findTask(taskId);
		requireManageSubtasks(task, userId);
		subtaskRepository.delete(findSubtask(taskId, subtaskId));
		reindex(taskId);
	}

	private void placeSubtask(Subtask subtask, int position) {
		List<Subtask> subtasks = subtaskRepository.findByTaskIdOrderByPosition(subtask.getTask().getId());
		subtasks.remove(subtask);
		int index = Math.max(0, Math.min(position, subtasks.size()));
		subtasks.add(index, subtask);
		for (int i = 0; i < subtasks.size(); i++) {
			subtasks.get(i).changePosition(i);
		}
	}

	private void reindex(Long taskId) {
		List<Subtask> subtasks = subtaskRepository.findByTaskIdOrderByPosition(taskId);
		for (int i = 0; i < subtasks.size(); i++) {
			subtasks.get(i).changePosition(i);
		}
	}

	private void requireManageSubtasks(Task task, Long userId) {
		BoardMember actor = boardPermissionService.requireMembership(task.getColumn().getBoard().getId(), userId);
		boardPermissionService.checkManageSubtasks(actor, assigneeId(task));
	}

	private void requireView(Task task, Long userId) {
		BoardMember actor = boardPermissionService.requireMembership(task.getColumn().getBoard().getId(), userId);
		boardPermissionService.check(actor, BoardAction.VIEW_BOARD);
	}

	private Long assigneeId(Task task) {
		return task.getAssignee() == null ? null : task.getAssignee().getId();
	}

	private Task findTask(Long taskId) {
		return taskRepository.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task not found"));
	}

	private Subtask findSubtask(Long taskId, Long subtaskId) {
		return subtaskRepository.findByIdAndTaskId(subtaskId, taskId)
			.orElseThrow(() -> new ResourceNotFoundException("Subtask not found"));
	}

}
