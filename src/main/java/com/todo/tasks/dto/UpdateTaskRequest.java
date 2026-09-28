package com.todo.tasks.dto;

import jakarta.validation.constraints.Size;

public record UpdateTaskRequest(

		@Size(max = 255) String title,

		String description,

		Long columnId,

		Integer position) {

}
