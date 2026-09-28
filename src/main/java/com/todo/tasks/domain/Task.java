package com.todo.tasks.domain;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.todo.columns.domain.BoardColumn;
import com.todo.users.domain.User;

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
@Table(name = "tasks")
public class Task {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "column_id", nullable = false)
	private BoardColumn column;

	@Column(nullable = false, length = 255)
	private String title;

	@Column(columnDefinition = "text")
	private String description;

	@Column(nullable = false)
	private Integer position;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assignee_id")
	private User assignee;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Task() {
	}

	public Task(BoardColumn column, String title, String description, Integer position, User createdBy) {
		this.column = column;
		this.title = title;
		this.description = description;
		this.position = position;
		this.createdBy = createdBy;
	}

	public void updateDetails(String title, String description) {
		if (title != null) {
			this.title = title;
		}
		if (description != null) {
			this.description = description;
		}
	}

	public void moveTo(BoardColumn column, Integer position) {
		this.column = column;
		this.position = position;
	}

	public void changePosition(Integer position) {
		this.position = position;
	}

	public void assign(User assignee) {
		this.assignee = assignee;
	}

	public Long getId() {
		return id;
	}

	public BoardColumn getColumn() {
		return column;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public Integer getPosition() {
		return position;
	}

	public User getAssignee() {
		return assignee;
	}

	public User getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
