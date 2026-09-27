package com.todo.tasks.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.todo.tasks.domain.Task;

public interface TaskRepository extends JpaRepository<Task, Long> {

	@EntityGraph(attributePaths = { "assignee", "createdBy" })
	List<Task> findByColumnIdOrderByPosition(Long columnId);

	@EntityGraph(attributePaths = { "assignee", "createdBy" })
	List<Task> findByColumnIdInOrderByPosition(Collection<Long> columnIds);

}
