package com.todo.boards.dto;

import java.util.List;

import com.todo.boards.domain.BoardType;
import com.todo.columns.dto.ColumnResponse;

public record BoardDetailResponse(Long id, String title, String description, BoardType type, Long ownerId,
		List<BoardMemberResponse> members, List<ColumnResponse> columns) {

}
