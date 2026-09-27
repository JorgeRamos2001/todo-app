package com.todo.tasks.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.todo.tasks.domain.Subtask;

public interface SubtaskRepository extends JpaRepository<Subtask, Long> {

	List<Subtask> findByTaskIdOrderByPosition(Long taskId);

	Optional<Subtask> findByIdAndTaskId(Long id, Long taskId);

}
