package com.todo.boards.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.todo.boards.domain.BoardActivity;

public interface BoardActivityRepository extends JpaRepository<BoardActivity, Long> {

	@EntityGraph(attributePaths = "actor")
	Page<BoardActivity> findByBoardId(Long boardId, Pageable pageable);

}
