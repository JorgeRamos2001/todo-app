package com.todo.boards.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.todo.boards.domain.BoardMember;

public interface BoardMemberRepository extends JpaRepository<BoardMember, Long> {

	@EntityGraph(attributePaths = "board")
	List<BoardMember> findByUserId(Long userId);

	@EntityGraph(attributePaths = "user")
	List<BoardMember> findByBoardId(Long boardId);

	Optional<BoardMember> findByBoardIdAndUserId(Long boardId, Long userId);

}
