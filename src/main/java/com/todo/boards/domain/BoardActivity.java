package com.todo.boards.domain;

import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(name = "board_activities")
public class BoardActivity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "board_id", nullable = false)
	private Board board;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "actor_id", nullable = false)
	private User actor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private BoardActivityAction action;

	@Enumerated(EnumType.STRING)
	@Column(name = "entity_type", nullable = false, length = 50)
	private BoardActivityEntityType entityType;

	@Column(name = "entity_id")
	private Long entityId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, Object> details;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected BoardActivity() {
	}

	public BoardActivity(Board board, User actor, BoardActivityAction action, BoardActivityEntityType entityType,
			Long entityId, Map<String, Object> details) {
		this.board = board;
		this.actor = actor;
		this.action = action;
		this.entityType = entityType;
		this.entityId = entityId;
		this.details = details;
	}

	public Long getId() {
		return id;
	}

	public Board getBoard() {
		return board;
	}

	public User getActor() {
		return actor;
	}

	public BoardActivityAction getAction() {
		return action;
	}

	public BoardActivityEntityType getEntityType() {
		return entityType;
	}

	public Long getEntityId() {
		return entityId;
	}

	public Map<String, Object> getDetails() {
		return details;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
