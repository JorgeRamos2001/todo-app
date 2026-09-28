package com.todo.invitations.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.todo.boards.domain.BoardAction;
import com.todo.boards.domain.BoardMember;
import com.todo.boards.repository.BoardMemberRepository;
import com.todo.boards.service.BoardPermissionService;
import com.todo.invitations.config.InvitationProperties;
import com.todo.invitations.domain.Invitation;
import com.todo.invitations.domain.InvitationStatus;
import com.todo.invitations.dto.CreateInvitationRequest;
import com.todo.invitations.dto.InvitationResponse;
import com.todo.invitations.repository.InvitationRepository;
import com.todo.mail.MailSender;
import com.todo.shared.error.ConflictException;
import com.todo.shared.error.ForbiddenException;
import com.todo.shared.error.ResourceNotFoundException;
import com.todo.shared.util.SecureTokens;
import com.todo.users.domain.User;
import com.todo.users.repository.UserRepository;

@Service
public class InvitationService {

	private final InvitationRepository invitationRepository;

	private final BoardMemberRepository boardMemberRepository;

	private final UserRepository userRepository;

	private final BoardPermissionService boardPermissionService;

	private final MailSender mailSender;

	private final InvitationEmailComposer emailComposer;

	private final InvitationProperties invitationProperties;

	public InvitationService(InvitationRepository invitationRepository, BoardMemberRepository boardMemberRepository,
			UserRepository userRepository, BoardPermissionService boardPermissionService, MailSender mailSender,
			InvitationEmailComposer emailComposer, InvitationProperties invitationProperties) {
		this.invitationRepository = invitationRepository;
		this.boardMemberRepository = boardMemberRepository;
		this.userRepository = userRepository;
		this.boardPermissionService = boardPermissionService;
		this.mailSender = mailSender;
		this.emailComposer = emailComposer;
		this.invitationProperties = invitationProperties;
	}

	@Transactional
	public InvitationResponse create(Long boardId, Long userId, CreateInvitationRequest request) {
		BoardMember actor = boardPermissionService.requireMembership(boardId, userId);
		boardPermissionService.checkInvite(actor, request.role());
		String email = normalizeEmail(request.email());
		userRepository.findByEmail(email).ifPresent((invitee) -> {
			if (boardMemberRepository.findByBoardIdAndUserId(boardId, invitee.getId()).isPresent()) {
				throw new ConflictException("User is already a member of this board");
			}
		});
		if (invitationRepository.existsByBoardIdAndInviteeEmailAndStatus(boardId, email, InvitationStatus.PENDING)) {
			throw new ConflictException("There is already a pending invitation for this email");
		}
		Invitation invitation = invitationRepository.save(new Invitation(actor.getBoard(), actor.getUser(), email,
				request.role(), SecureTokens.randomToken(), Instant.now().plus(invitationProperties.ttl())));
		mailSender.send(emailComposer.compose(invitation, invitationProperties));
		return InvitationResponse.from(invitation);
	}

	@Transactional(readOnly = true)
	public List<InvitationResponse> listSent(Long boardId, Long userId) {
		BoardMember actor = boardPermissionService.requireMembership(boardId, userId);
		boardPermissionService.check(actor, BoardAction.INVITE_MEMBERS);
		return invitationRepository.findByBoardIdOrderByIdDesc(boardId)
			.stream()
			.map(InvitationResponse::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<InvitationResponse> listReceived(Long userId) {
		User user = findUser(userId);
		return invitationRepository
			.findByInviteeEmailAndStatusOrderByIdDesc(user.getEmail(), InvitationStatus.PENDING)
			.stream()
			.map(InvitationResponse::from)
			.toList();
	}

	@Transactional(noRollbackFor = ConflictException.class)
	public InvitationResponse accept(String token, Long userId) {
		Invitation invitation = findPending(token);
		User user = findUser(userId);
		requireInvitee(invitation, user);
		if (boardMemberRepository.findByBoardIdAndUserId(invitation.getBoard().getId(), user.getId()).isEmpty()) {
			boardMemberRepository.save(new BoardMember(invitation.getBoard(), user, invitation.getRole()));
		}
		invitation.accept();
		return InvitationResponse.from(invitation);
	}

	@Transactional(noRollbackFor = ConflictException.class)
	public InvitationResponse reject(String token, Long userId) {
		Invitation invitation = findPending(token);
		User user = findUser(userId);
		requireInvitee(invitation, user);
		invitation.reject();
		return InvitationResponse.from(invitation);
	}

	private Invitation findPending(String token) {
		Invitation invitation = invitationRepository.findByToken(token)
			.orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
		if (invitation.getStatus() != InvitationStatus.PENDING) {
			throw new ConflictException("Invitation is no longer pending");
		}
		if (invitation.isExpired()) {
			invitation.expire();
			throw new ConflictException("Invitation has expired");
		}
		return invitation;
	}

	private void requireInvitee(Invitation invitation, User user) {
		if (!invitation.getInviteeEmail().equals(user.getEmail())) {
			throw new ForbiddenException("Invitation is for a different email address");
		}
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
