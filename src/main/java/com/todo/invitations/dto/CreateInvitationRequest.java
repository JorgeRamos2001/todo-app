package com.todo.invitations.dto;

import com.todo.boards.domain.BoardRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInvitationRequest(

		@NotBlank @Email @Size(max = 255) String email,

		@NotNull BoardRole role) {

}
