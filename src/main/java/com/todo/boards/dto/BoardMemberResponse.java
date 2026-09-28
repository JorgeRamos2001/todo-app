package com.todo.boards.dto;

import com.todo.boards.domain.BoardRole;

public record BoardMemberResponse(Long id, Long userId, String name, String email, BoardRole role) {

}
