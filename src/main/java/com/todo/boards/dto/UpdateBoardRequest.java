package com.todo.boards.dto;

import jakarta.validation.constraints.Size;

public record UpdateBoardRequest(

		@Size(max = 255) String title,

		String description) {

}
