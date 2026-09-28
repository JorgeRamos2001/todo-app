package com.todo.tasks.domain;

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
@Table(name = "subtasks")
public class Subtask {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "task_id", nullable = false)
	private Task task;

	@Column(nullable = false, length = 255)
	private String title;

	@Column(nullable = false)
	private boolean done;

	@Column(nullable = false)
	private Integer position;

	protected Subtask() {
	}

	public Subtask(Task task, String title, Integer position) {
		this.task = task;
		this.title = title;
		this.position = position;
		this.done = false;
	}

	public void rename(String title) {
		this.title = title;
	}

	public void markDone(boolean done) {
		this.done = done;
	}

	public void changePosition(Integer position) {
		this.position = position;
	}

	public Long getId() {
		return id;
	}

	public Task getTask() {
		return task;
	}

	public String getTitle() {
		return title;
	}

	public boolean isDone() {
		return done;
	}

	public Integer getPosition() {
		return position;
	}

}
