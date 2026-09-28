package com.todo.boards.dto;

import com.todo.boards.domain.BoardRole;
import com.todo.boards.domain.BoardType;

public record BoardSummaryResponse(Long id, String title, BoardType type, Long ownerId, BoardRole role) {

}
