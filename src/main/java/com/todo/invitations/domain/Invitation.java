package com.todo.invitations.domain;

import java.time.Instant;

import com.todo.boards.domain.Board;
import com.todo.boards.domain.BoardRole;
import com.todo.users.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "invitations")
public class Invitation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "board_id", nullable = false)
	private Board board;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "inviter_id", nullable = false)
	private User inviter;

	@Column(name = "invitee_email", nullable = false, length = 255)
	private String inviteeEmail;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BoardRole role;

	@Column(nullable = false, unique = true)
	private String token;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private InvitationStatus status;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected Invitation() {
	}

	public Invitation(Board board, User inviter, String inviteeEmail, BoardRole role, String token,
			Instant expiresAt) {
		this.board = board;
		this.inviter = inviter;
		this.inviteeEmail = inviteeEmail;
		this.role = role;
		this.token = token;
		this.status = InvitationStatus.PENDING;
		this.expiresAt = expiresAt;
	}

	public boolean isExpired() {
		return expiresAt.isBefore(Instant.now());
	}

	public void accept() {
		this.status = InvitationStatus.ACCEPTED;
	}

	public void reject() {
		this.status = InvitationStatus.REJECTED;
	}

	public void expire() {
		this.status = InvitationStatus.EXPIRED;
	}

	public Long getId() {
		return id;
	}

	public Board getBoard() {
		return board;
	}

	public User getInviter() {
		return inviter;
	}

	public String getInviteeEmail() {
		return inviteeEmail;
	}

	public BoardRole getRole() {
		return role;
	}

	public String getToken() {
		return token;
	}

	public InvitationStatus getStatus() {
		return status;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

}
