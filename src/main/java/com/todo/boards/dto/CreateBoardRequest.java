package com.todo.boards.dto;

import com.todo.boards.domain.BoardType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBoardRequest(

		@NotBlank @Size(max = 255) String title,

		String description,

		@NotNull BoardType type) {

}
