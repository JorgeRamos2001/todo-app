package com.todo.tasks.dto;

import jakarta.validation.constraints.NotNull;

public record AssignTaskRequest(@NotNull Long userId) {

}
