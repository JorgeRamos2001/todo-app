package com.todo.boards.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.todo.boards.domain.Board;

public interface BoardRepository extends JpaRepository<Board, Long> {

}
