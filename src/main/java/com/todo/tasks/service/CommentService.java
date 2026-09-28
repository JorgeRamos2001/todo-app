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
import com.todo.boards.service.BoardPermissionService;
import com.todo.shared.error.ResourceNotFoundException;
import com.todo.tasks.domain.Comment;
import com.todo.tasks.domain.Task;
import com.todo.tasks.dto.CommentResponse;
import com.todo.tasks.dto.CreateCommentRequest;
import com.todo.tasks.repository.CommentRepository;
import com.todo.tasks.repository.TaskRepository;

@Service
public class CommentService {

	private final CommentRepository commentRepository;

	private final TaskRepository taskRepository;

	private final BoardPermissionService boardPermissionService;

	private final BoardEventPublisher boardEventPublisher;

	public CommentService(CommentRepository commentRepository, TaskRepository taskRepository,
			BoardPermissionService boardPermissionService, BoardEventPublisher boardEventPublisher) {
		this.commentRepository = commentRepository;
		this.taskRepository = taskRepository;
		this.boardPermissionService = boardPermissionService;
		this.boardEventPublisher = boardEventPublisher;
	}

	@Transactional
	public CommentResponse create(Long taskId, Long userId, CreateCommentRequest request) {
		Task task = findTask(taskId);
		BoardMember actor = requireMembership(task, userId);
		boardPermissionService.check(actor, BoardAction.COMMENT_TASK);
		Comment comment = commentRepository.save(new Comment(task, actor.getUser(), request.content().trim()));
		boardEventPublisher.publish(task.getColumn().getBoard().getId(), userId, BoardActivityAction.COMMENT_CREATED,
				BoardActivityEntityType.COMMENT, comment.getId(), Map.of("preview", preview(comment.getContent())));
		return CommentResponse.from(comment);
	}

	@Transactional(readOnly = true)
	public List<CommentResponse> list(Long taskId, Long userId) {
		Task task = findTask(taskId);
		BoardMember actor = requireMembership(task, userId);
		boardPermissionService.check(actor, BoardAction.VIEW_BOARD);
		return commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId)
			.stream()
			.map(CommentResponse::from)
			.toList();
	}

	@Transactional
	public void delete(Long taskId, Long commentId, Long userId) {
		Task task = findTask(taskId);
		BoardMember actor = requireMembership(task, userId);
		Comment comment = commentRepository.findByIdAndTaskId(commentId, taskId)
			.orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
		boardPermissionService.checkDeleteComment(actor, comment.getAuthor().getId());
		boardEventPublisher.publish(task.getColumn().getBoard().getId(), userId, BoardActivityAction.COMMENT_DELETED,
				BoardActivityEntityType.COMMENT, commentId, Map.of("authorId", comment.getAuthor().getId()));
		commentRepository.delete(comment);
	}

	private BoardMember requireMembership(Task task, Long userId) {
		return boardPermissionService.requireMembership(task.getColumn().getBoard().getId(), userId);
	}

	private Task findTask(Long taskId) {
		return taskRepository.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task not found"));
	}

	private String preview(String content) {
		return content.length() <= 120 ? content : content.substring(0, 120) + "...";
	}

}
