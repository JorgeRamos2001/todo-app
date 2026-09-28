package com.todo.invitations.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.todo.auth.security.AuthenticatedUser;
import com.todo.invitations.dto.CreateInvitationRequest;
import com.todo.invitations.dto.InvitationResponse;
import com.todo.invitations.service.InvitationService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Invitations", description = "Invitaciones por correo y aceptar/rechazar")
@RestController
public class InvitationController {

	private final InvitationService invitationService;

	public InvitationController(InvitationService invitationService) {
		this.invitationService = invitationService;
	}

	@PostMapping("/api/v1/boards/{boardId}/invitations")
	@ResponseStatus(HttpStatus.CREATED)
	InvitationResponse create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long boardId,
			@Valid @RequestBody CreateInvitationRequest request) {
		return invitationService.create(boardId, user.id(), request);
	}

	@GetMapping("/api/v1/boards/{boardId}/invitations")
	List<InvitationResponse> listSent(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long boardId) {
		return invitationService.listSent(boardId, user.id());
	}

	@GetMapping("/api/v1/invitations")
	List<InvitationResponse> listReceived(@AuthenticationPrincipal AuthenticatedUser user) {
		return invitationService.listReceived(user.id());
	}

	@PostMapping("/api/v1/invitations/{token}/accept")
	InvitationResponse accept(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String token) {
		return invitationService.accept(token, user.id());
	}

	@PostMapping("/api/v1/invitations/{token}/reject")
	InvitationResponse reject(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String token) {
		return invitationService.reject(token, user.id());
	}

}
