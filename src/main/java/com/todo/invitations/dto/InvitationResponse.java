package com.todo.invitations.dto;

import java.time.Instant;

import com.todo.boards.domain.BoardRole;
import com.todo.invitations.domain.Invitation;
import com.todo.invitations.domain.InvitationStatus;

public record InvitationResponse(Long id, Long boardId, String boardTitle, String inviterName, String inviteeEmail,
		BoardRole role, InvitationStatus status, String token, Instant expiresAt) {

	public static InvitationResponse from(Invitation invitation) {
		return new InvitationResponse(invitation.getId(), invitation.getBoard().getId(),
				invitation.getBoard().getTitle(), invitation.getInviter().getName(), invitation.getInviteeEmail(),
				invitation.getRole(), invitation.getStatus(), invitation.getToken(), invitation.getExpiresAt());
	}

}
