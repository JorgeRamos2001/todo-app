package com.todo.boards.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.todo.boards.domain.Board;
import com.todo.boards.domain.BoardAction;
import com.todo.boards.domain.BoardMember;
import com.todo.boards.domain.BoardRole;
import com.todo.boards.domain.BoardType;
import com.todo.boards.repository.BoardMemberRepository;
import com.todo.shared.error.ForbiddenException;
import com.todo.users.domain.User;

class BoardPermissionServiceTests {

	private final BoardMemberRepository boardMemberRepository = mock(BoardMemberRepository.class);

	private final BoardPermissionService service = new BoardPermissionService(boardMemberRepository);

	@ParameterizedTest(name = "{0} + {1} -> allowed={2}")
	@MethodSource("roleMatrix")
	void enforcesRoleMatrix(BoardRole role, BoardAction action, boolean allowed) {
		BoardMember member = collaborativeMember(role, 10L);
		if (allowed) {
			assertThatCode(() -> service.check(member, action)).doesNotThrowAnyException();
		}
		else {
			assertThatThrownBy(() -> service.check(member, action)).isInstanceOf(ForbiddenException.class);
		}
	}

	static Stream<Arguments> roleMatrix() {
		return Stream.of(matrix(BoardAction.VIEW_BOARD, BoardRole.OWNER, BoardRole.ADMIN, BoardRole.MEMBER),
				matrix(BoardAction.EDIT_BOARD, BoardRole.OWNER),
				matrix(BoardAction.DELETE_BOARD, BoardRole.OWNER),
				matrix(BoardAction.VIEW_ACTIVITIES, BoardRole.OWNER),
				matrix(BoardAction.CHANGE_ROLES, BoardRole.OWNER),
				matrix(BoardAction.INVITE_MEMBERS, BoardRole.OWNER, BoardRole.ADMIN),
				matrix(BoardAction.REMOVE_MEMBERS, BoardRole.OWNER, BoardRole.ADMIN),
				matrix(BoardAction.MANAGE_COLUMNS, BoardRole.OWNER, BoardRole.ADMIN),
				matrix(BoardAction.CREATE_TASK, BoardRole.OWNER, BoardRole.ADMIN),
				matrix(BoardAction.DELETE_TASK, BoardRole.OWNER, BoardRole.ADMIN),
				matrix(BoardAction.ASSIGN_TASK, BoardRole.OWNER, BoardRole.ADMIN),
				matrix(BoardAction.EDIT_TASK, BoardRole.OWNER, BoardRole.ADMIN, BoardRole.MEMBER),
				matrix(BoardAction.MOVE_TASK, BoardRole.OWNER, BoardRole.ADMIN, BoardRole.MEMBER),
				matrix(BoardAction.MANAGE_SUBTASKS, BoardRole.OWNER, BoardRole.ADMIN, BoardRole.MEMBER),
				matrix(BoardAction.COMMENT_TASK, BoardRole.OWNER, BoardRole.ADMIN, BoardRole.MEMBER),
				matrix(BoardAction.DELETE_COMMENT, BoardRole.OWNER, BoardRole.ADMIN, BoardRole.MEMBER))
			.flatMap((stream) -> stream);
	}

	private static Stream<Arguments> matrix(BoardAction action, BoardRole... allowedRoles) {
		Set<BoardRole> allowed = Set.of(allowedRoles);
		return Stream.of(BoardRole.values()).map((role) -> Arguments.of(role, action, allowed.contains(role)));
	}

	@Test
	void personalBoardsBlockInvitationsEvenForOwner() {
		BoardMember owner = member(BoardType.PERSONAL, BoardRole.OWNER, 1L);
		assertThatThrownBy(() -> service.checkInvite(owner, BoardRole.MEMBER)).isInstanceOf(ForbiddenException.class)
			.hasMessageContaining("Personal boards");
	}

	@Test
	void ownerCanInviteAdminOrMember() {
		BoardMember owner = collaborativeMember(BoardRole.OWNER, 1L);
		assertThatCode(() -> service.checkInvite(owner, BoardRole.ADMIN)).doesNotThrowAnyException();
		assertThatCode(() -> service.checkInvite(owner, BoardRole.MEMBER)).doesNotThrowAnyException();
	}

	@Test
	void ownerCannotInviteAnotherOwner() {
		BoardMember owner = collaborativeMember(BoardRole.OWNER, 1L);
		assertThatThrownBy(() -> service.checkInvite(owner, BoardRole.OWNER)).isInstanceOf(ForbiddenException.class);
	}

	@Test
	void adminCanOnlyInviteMembers() {
		BoardMember admin = collaborativeMember(BoardRole.ADMIN, 2L);
		assertThatCode(() -> service.checkInvite(admin, BoardRole.MEMBER)).doesNotThrowAnyException();
		assertThatThrownBy(() -> service.checkInvite(admin, BoardRole.ADMIN)).isInstanceOf(ForbiddenException.class);
	}

	@Test
	void memberCannotInvite() {
		BoardMember member = collaborativeMember(BoardRole.MEMBER, 3L);
		assertThatThrownBy(() -> service.checkInvite(member, BoardRole.MEMBER)).isInstanceOf(ForbiddenException.class);
	}

	@Test
	void ownerCanRemoveAnyMember() {
		BoardMember owner = collaborativeMember(BoardRole.OWNER, 1L);
		assertThatCode(() -> service.checkRemoveMember(owner, collaborativeMember(BoardRole.ADMIN, 2L)))
			.doesNotThrowAnyException();
		assertThatCode(() -> service.checkRemoveMember(owner, collaborativeMember(BoardRole.MEMBER, 3L)))
			.doesNotThrowAnyException();
	}

	@Test
	void ownerCannotBeRemoved() {
		BoardMember owner = collaborativeMember(BoardRole.OWNER, 1L);
		assertThatThrownBy(() -> service.checkRemoveMember(owner, collaborativeMember(BoardRole.OWNER, 4L)))
			.isInstanceOf(ForbiddenException.class)
			.hasMessageContaining("owner");
	}

	@Test
	void adminCanOnlyRemoveMembers() {
		BoardMember admin = collaborativeMember(BoardRole.ADMIN, 2L);
		assertThatCode(() -> service.checkRemoveMember(admin, collaborativeMember(BoardRole.MEMBER, 3L)))
			.doesNotThrowAnyException();
		assertThatThrownBy(() -> service.checkRemoveMember(admin, collaborativeMember(BoardRole.ADMIN, 4L)))
			.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void ownerCanChangeMemberRoles() {
		BoardMember owner = collaborativeMember(BoardRole.OWNER, 1L);
		assertThatCode(() -> service.checkChangeRole(owner, collaborativeMember(BoardRole.MEMBER, 3L), BoardRole.ADMIN))
			.doesNotThrowAnyException();
	}

	@Test
	void adminCannotChangeRoles() {
		BoardMember admin = collaborativeMember(BoardRole.ADMIN, 2L);
		assertThatThrownBy(
				() -> service.checkChangeRole(admin, collaborativeMember(BoardRole.MEMBER, 3L), BoardRole.ADMIN))
			.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void ownerRoleCannotBeChangedOrAssigned() {
		BoardMember owner = collaborativeMember(BoardRole.OWNER, 1L);
		assertThatThrownBy(
				() -> service.checkChangeRole(owner, collaborativeMember(BoardRole.OWNER, 4L), BoardRole.ADMIN))
			.isInstanceOf(ForbiddenException.class);
		assertThatThrownBy(
				() -> service.checkChangeRole(owner, collaborativeMember(BoardRole.MEMBER, 3L), BoardRole.OWNER))
			.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void memberCanOnlyEditAssignedTask() {
		BoardMember member = collaborativeMember(BoardRole.MEMBER, 7L);
		assertThatCode(() -> service.checkEditTask(member, 7L)).doesNotThrowAnyException();
		assertThatThrownBy(() -> service.checkEditTask(member, 8L)).isInstanceOf(ForbiddenException.class);
		assertThatThrownBy(() -> service.checkEditTask(member, null)).isInstanceOf(ForbiddenException.class);
	}

	@Test
	void memberCanOnlyMoveAssignedTask() {
		BoardMember member = collaborativeMember(BoardRole.MEMBER, 7L);
		assertThatCode(() -> service.checkMoveTask(member, 7L)).doesNotThrowAnyException();
		assertThatThrownBy(() -> service.checkMoveTask(member, 8L)).isInstanceOf(ForbiddenException.class);
	}

	@Test
	void memberCanOnlyManageSubtasksOfAssignedTask() {
		BoardMember member = collaborativeMember(BoardRole.MEMBER, 7L);
		assertThatCode(() -> service.checkManageSubtasks(member, 7L)).doesNotThrowAnyException();
		assertThatThrownBy(() -> service.checkManageSubtasks(member, 8L)).isInstanceOf(ForbiddenException.class);
	}

	@Test
	void ownerAndAdminCanEditAnyTask() {
		assertThatCode(() -> service.checkEditTask(collaborativeMember(BoardRole.OWNER, 1L), null))
			.doesNotThrowAnyException();
		assertThatCode(() -> service.checkEditTask(collaborativeMember(BoardRole.ADMIN, 2L), null))
			.doesNotThrowAnyException();
	}

	@Test
	void memberCanOnlyDeleteOwnComments() {
		BoardMember member = collaborativeMember(BoardRole.MEMBER, 7L);
		assertThatCode(() -> service.checkDeleteComment(member, 7L)).doesNotThrowAnyException();
		assertThatThrownBy(() -> service.checkDeleteComment(member, 8L)).isInstanceOf(ForbiddenException.class);
	}

	@Test
	void ownerAndAdminCanDeleteAnyComment() {
		assertThatCode(() -> service.checkDeleteComment(collaborativeMember(BoardRole.OWNER, 1L), 9L))
			.doesNotThrowAnyException();
		assertThatCode(() -> service.checkDeleteComment(collaborativeMember(BoardRole.ADMIN, 2L), 9L))
			.doesNotThrowAnyException();
	}

	private BoardMember collaborativeMember(BoardRole role, long userId) {
		return member(BoardType.COLLABORATIVE, role, userId);
	}

	private BoardMember member(BoardType type, BoardRole role, long userId) {
		Board board = new Board("Board", null, type, user(1L));
		return new BoardMember(board, user(userId), role);
	}

	private User user(long id) {
		User user = mock(User.class);
		when(user.getId()).thenReturn(id);
		return user;
	}

}
