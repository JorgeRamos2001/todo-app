package com.todo.columns.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.todo.columns.domain.BoardColumn;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, Long> {

	List<BoardColumn> findByBoardIdOrderByPosition(Long boardId);

	Optional<BoardColumn> findByIdAndBoardId(Long id, Long boardId);

}
