package com.todo.boards.domain;

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
@Table(name = "board_members")
public class BoardMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "board_id", nullable = false)
	private Board board;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BoardRole role;

	protected BoardMember() {
	}

	public BoardMember(Board board, User user, BoardRole role) {
		this.board = board;
		this.user = user;
		this.role = role;
	}

	public void changeRole(BoardRole role) {
		this.role = role;
	}

	public Long getId() {
		return id;
	}

	public Board getBoard() {
		return board;
	}

	public User getUser() {
		return user;
	}

	public BoardRole getRole() {
		return role;
	}

}
