package com.todo.boards.service;

import org.springframework.stereotype.Service;

import com.todo.boards.domain.BoardAction;
import com.todo.boards.domain.BoardMember;
import com.todo.boards.domain.BoardRole;
import com.todo.boards.domain.BoardType;
import com.todo.boards.repository.BoardMemberRepository;
import com.todo.shared.error.ForbiddenException;

@Service
public class BoardPermissionService {

	private final BoardMemberRepository boardMemberRepository;

	public BoardPermissionService(BoardMemberRepository boardMemberRepository) {
		this.boardMemberRepository = boardMemberRepository;
	}

	public BoardMember requireMembership(Long boardId, Long userId) {
		return boardMemberRepository.findByBoardIdAndUserId(boardId, userId)
			.orElseThrow(() -> new ForbiddenException("You are not a member of this board"));
	}

	public void check(BoardMember member, BoardAction action) {
		if (!isAllowed(member.getRole(), action)) {
			throw new ForbiddenException("Role " + member.getRole() + " cannot perform " + action);
		}
	}

	public void checkInvite(BoardMember actor, BoardRole targetRole) {
		if (actor.getBoard().getType() == BoardType.PERSONAL) {
			throw new ForbiddenException("Personal boards cannot have members");
		}
		check(actor, BoardAction.INVITE_MEMBERS);
		if (targetRole == BoardRole.OWNER) {
			throw new ForbiddenException("Cannot invite a user as owner");
		}
		if (actor.getRole() == BoardRole.ADMIN && targetRole != BoardRole.MEMBER) {
			throw new ForbiddenException("Admins can only invite members");
		}
	}

	public void checkRemoveMember(BoardMember actor, BoardMember target) {
		check(actor, BoardAction.REMOVE_MEMBERS);
		if (target.getRole() == BoardRole.OWNER) {
			throw new ForbiddenException("The owner cannot be removed");
		}
		if (actor.getRole() == BoardRole.ADMIN && target.getRole() != BoardRole.MEMBER) {
			throw new ForbiddenException("Admins can only remove members");
		}
	}

	public void checkChangeRole(BoardMember actor, BoardMember target, BoardRole newRole) {
		check(actor, BoardAction.CHANGE_ROLES);
		if (target.getRole() == BoardRole.OWNER) {
			throw new ForbiddenException("The owner role cannot be changed");
		}
		if (newRole == BoardRole.OWNER) {
			throw new ForbiddenException("Cannot assign the owner role");
		}
	}

	public void checkEditTask(BoardMember actor, Long assigneeId) {
		checkAssignedTask(actor, assigneeId, BoardAction.EDIT_TASK);
	}

	public void checkMoveTask(BoardMember actor, Long assigneeId) {
		checkAssignedTask(actor, assigneeId, BoardAction.MOVE_TASK);
	}

	public void checkManageSubtasks(BoardMember actor, Long assigneeId) {
		checkAssignedTask(actor, assigneeId, BoardAction.MANAGE_SUBTASKS);
	}

	public void checkDeleteComment(BoardMember actor, Long authorId) {
		if (actor.getRole() != BoardRole.MEMBER) {
			return;
		}
		if (!actor.getUser().getId().equals(authorId)) {
			throw new ForbiddenException("Members can only delete their own comments");
		}
	}

	private void checkAssignedTask(BoardMember actor, Long assigneeId, BoardAction action) {
		if (actor.getRole() != BoardRole.MEMBER) {
			return;
		}
		if (assigneeId == null || !assigneeId.equals(actor.getUser().getId())) {
			throw new ForbiddenException("Members can only perform " + action + " on tasks assigned to them");
		}
	}

	private boolean isAllowed(BoardRole role, BoardAction action) {
		return switch (action) {
			case VIEW_BOARD -> true;
			case EDIT_BOARD, DELETE_BOARD, VIEW_ACTIVITIES, CHANGE_ROLES -> role == BoardRole.OWNER;
			case INVITE_MEMBERS, REMOVE_MEMBERS, MANAGE_COLUMNS, CREATE_TASK, DELETE_TASK, ASSIGN_TASK ->
				role == BoardRole.OWNER || role == BoardRole.ADMIN;
			case EDIT_TASK, MOVE_TASK, MANAGE_SUBTASKS, COMMENT_TASK, DELETE_COMMENT -> true;
		};
	}

}
