package com.todo.columns.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateColumnRequest(@NotBlank @Size(max = 255) String name) {

}
