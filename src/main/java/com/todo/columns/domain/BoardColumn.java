package com.todo.columns.domain;

import com.todo.boards.domain.Board;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "board_columns")
public class BoardColumn {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "board_id", nullable = false)
	private Board board;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(nullable = false)
	private Integer position;

	protected BoardColumn() {
	}

	public BoardColumn(Board board, String name, Integer position) {
		this.board = board;
		this.name = name;
		this.position = position;
	}

	public void rename(String name) {
		this.name = name;
	}

	public void changePosition(Integer position) {
		this.position = position;
	}

	public Long getId() {
		return id;
	}

	public Board getBoard() {
		return board;
	}

	public String getName() {
		return name;
	}

	public Integer getPosition() {
		return position;
	}

}
