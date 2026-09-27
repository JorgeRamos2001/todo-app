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
import jakarta.persistence.Version;

@Entity
@Table(name = "boards")
public class Board {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 255)
	private String title;

	@Column(columnDefinition = "text")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BoardType type;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner;

	@Version
	@Column(nullable = false)
	private Long version;

	protected Board() {
	}

	public Board(String title, String description, BoardType type, User owner) {
		this.title = title;
		this.description = description;
		this.type = type;
		this.owner = owner;
	}

	public void updateDetails(String title, String description) {
		if (title != null) {
			this.title = title;
		}
		if (description != null) {
			this.description = description;
		}
	}

	public boolean isPersonal() {
		return type == BoardType.PERSONAL;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public BoardType getType() {
		return type;
	}

	public User getOwner() {
		return owner;
	}

	public Long getVersion() {
		return version;
	}

}
