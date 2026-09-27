package com.todo.tasks.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.todo.tasks.domain.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	@EntityGraph(attributePaths = "author")
	List<Comment> findByTaskIdOrderByCreatedAtAsc(Long taskId);

	Optional<Comment> findByIdAndTaskId(Long id, Long taskId);

}
