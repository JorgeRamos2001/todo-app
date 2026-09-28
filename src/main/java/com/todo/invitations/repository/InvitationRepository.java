package com.todo.invitations.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.todo.invitations.domain.Invitation;
import com.todo.invitations.domain.InvitationStatus;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

	@EntityGraph(attributePaths = { "board", "inviter" })
	Optional<Invitation> findByToken(String token);

	@EntityGraph(attributePaths = { "board", "inviter" })
	List<Invitation> findByBoardIdOrderByIdDesc(Long boardId);

	@EntityGraph(attributePaths = { "board", "inviter" })
	List<Invitation> findByInviteeEmailAndStatusOrderByIdDesc(String inviteeEmail, InvitationStatus status);

	boolean existsByBoardIdAndInviteeEmailAndStatus(Long boardId, String inviteeEmail, InvitationStatus status);

}
