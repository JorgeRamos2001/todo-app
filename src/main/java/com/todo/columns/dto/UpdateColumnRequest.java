package com.todo.columns.dto;

import jakarta.validation.constraints.Size;

public record UpdateColumnRequest(

		@Size(max = 255) String name,

		Integer position) {

}
