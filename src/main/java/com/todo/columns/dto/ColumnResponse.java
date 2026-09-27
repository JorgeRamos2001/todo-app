package com.todo.columns.dto;

import java.util.List;

import com.todo.tasks.dto.TaskResponse;

public record ColumnResponse(Long id, String name, Integer position, List<TaskResponse> tasks) {

}
