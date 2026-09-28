package com.todo.invitations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.todo.auth.repository.RefreshTokenRepository;
import com.todo.auth.security.JwtService;
import com.todo.boards.domain.Board;
import com.todo.boards.domain.BoardMember;
import com.todo.boards.domain.BoardRole;
import com.todo.boards.domain.BoardType;
import com.todo.boards.repository.BoardMemberRepository;
import com.todo.boards.repository.BoardRepository;
import com.todo.invitations.domain.InvitationStatus;
import com.todo.invitations.dto.CreateInvitationRequest;
import com.todo.invitations.dto.InvitationResponse;
import com.todo.invitations.repository.InvitationRepository;
import com.todo.mail.MailMessage;
import com.todo.mail.MailSender;
import com.todo.users.domain.User;
import com.todo.users.repository.UserRepository;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class InvitationFlowIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	UserRepository userRepository;

	@Autowired
	BoardRepository boardRepository;

	@Autowired
	BoardMemberRepository boardMemberRepository;

	@Autowired
	InvitationRepository invitationRepository;

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	JwtService jwtService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	MailSender mailSender;

	private User alice;

	private User bob;

	private User carol;

	private Board board;

	@BeforeEach
	void setUp() {
		invitationRepository.deleteAll();
		boardMemberRepository.deleteAll();
		boardRepository.deleteAll();
		refreshTokenRepository.deleteAll();
		userRepository.deleteAll();
		alice = userRepository.save(User.local("Alice", "alice@example.com", "hash"));
		bob = userRepository.save(User.local("Bob", "bob@example.com", "hash"));
		carol = userRepository.save(User.local("Carol", "carol@example.com", "hash"));
		board = boardRepository.save(new Board("Board", null, BoardType.COLLABORATIVE, alice));
		boardMemberRepository.save(new BoardMember(board, alice, BoardRole.OWNER));
		boardMemberRepository.save(new BoardMember(board, bob, BoardRole.MEMBER));
	}

	@Test
	void ownerInvitesUserAndEmailIsSent() throws Exception {
		InvitationResponse invitation = invite(board.getId(), alice, carol.getEmail(), BoardRole.MEMBER);

		assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
		assertThat(invitation.token()).isNotBlank();

		ArgumentCaptor<MailMessage> captor = ArgumentCaptor.forClass(MailMessage.class);
		verify(mailSender).send(captor.capture());
		MailMessage message = captor.getValue();
		assertThat(message.to()).isEqualTo(carol.getEmail());
		assertThat(message.html()).contains(invitation.token());
	}

	@Test
	void adminCanOnlyInviteMembers() throws Exception {
		BoardMember bobMembership = boardMemberRepository
			.findByBoardIdAndUserId(board.getId(), bob.getId())
			.orElseThrow();
		bobMembership.changeRole(BoardRole.ADMIN);
		boardMemberRepository.save(bobMembership);

		mockMvc
			.perform(post("/api/v1/boards/{boardId}/invitations", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateInvitationRequest(carol.getEmail(), BoardRole.ADMIN))))
			.andExpect(status().isForbidden());

		invite(board.getId(), bob, carol.getEmail(), BoardRole.MEMBER);
	}

	@Test
	void memberCannotInvite() throws Exception {
		mockMvc
			.perform(post("/api/v1/boards/{boardId}/invitations", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateInvitationRequest(carol.getEmail(), BoardRole.MEMBER))))
			.andExpect(status().isForbidden());
	}

	@Test
	void personalBoardsCannotHaveInvitations() throws Exception {
		Board personal = boardRepository.save(new Board("Personal", null, BoardType.PERSONAL, alice));
		boardMemberRepository.save(new BoardMember(personal, alice, BoardRole.OWNER));

		mockMvc
			.perform(post("/api/v1/boards/{boardId}/invitations", personal.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateInvitationRequest(carol.getEmail(), BoardRole.MEMBER))))
			.andExpect(status().isForbidden());
	}

	@Test
	void cannotInviteExistingMember() throws Exception {
		mockMvc
			.perform(post("/api/v1/boards/{boardId}/invitations", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateInvitationRequest(bob.getEmail(), BoardRole.MEMBER))))
			.andExpect(status().isConflict());
	}

	@Test
	void duplicatePendingInvitationIsRejected() throws Exception {
		invite(board.getId(), alice, carol.getEmail(), BoardRole.MEMBER);

		mockMvc
			.perform(post("/api/v1/boards/{boardId}/invitations", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateInvitationRequest(carol.getEmail(), BoardRole.MEMBER))))
			.andExpect(status().isConflict());
	}

	@Test
	void acceptCreatesMembershipWithInvitationRole() throws Exception {
		InvitationResponse invitation = invite(board.getId(), alice, carol.getEmail(), BoardRole.MEMBER);

		mockMvc
			.perform(post("/api/v1/invitations/{token}/accept", invitation.token())
				.header(HttpHeaders.AUTHORIZATION, authorization(carol)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACCEPTED"));

		BoardMember membership = boardMemberRepository
			.findByBoardIdAndUserId(board.getId(), carol.getId())
			.orElseThrow();
		assertThat(membership.getRole()).isEqualTo(BoardRole.MEMBER);
	}

	@Test
	void acceptRequiresMatchingEmail() throws Exception {
		InvitationResponse invitation = invite(board.getId(), alice, carol.getEmail(), BoardRole.MEMBER);

		mockMvc
			.perform(post("/api/v1/invitations/{token}/accept", invitation.token())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isForbidden());
	}

	@Test
	void rejectedInvitationCannotBeAcceptedAndCanBeReinvited() throws Exception {
		InvitationResponse invitation = invite(board.getId(), alice, carol.getEmail(), BoardRole.MEMBER);

		mockMvc
			.perform(post("/api/v1/invitations/{token}/reject", invitation.token())
				.header(HttpHeaders.AUTHORIZATION, authorization(carol)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"));

		mockMvc
			.perform(post("/api/v1/invitations/{token}/accept", invitation.token())
				.header(HttpHeaders.AUTHORIZATION, authorization(carol)))
			.andExpect(status().isConflict());

		invite(board.getId(), alice, carol.getEmail(), BoardRole.MEMBER);
	}

	@Test
	void expiredInvitationIsRejectedAndMarkedExpired() throws Exception {
		InvitationResponse invitation = invite(board.getId(), alice, carol.getEmail(), BoardRole.MEMBER);
		jdbcTemplate.update("update invitations set expires_at = now() - interval '1 hour' where token = ?",
				invitation.token());

		mockMvc
			.perform(post("/api/v1/invitations/{token}/accept", invitation.token())
				.header(HttpHeaders.AUTHORIZATION, authorization(carol)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Invitation has expired"));

		assertThat(invitationRepository.findByToken(invitation.token()).orElseThrow().getStatus())
			.isEqualTo(InvitationStatus.EXPIRED);
	}

	@Test
	void receivedInvitationsAreListedByEmail() throws Exception {
		invite(board.getId(), alice, carol.getEmail(), BoardRole.MEMBER);

		mockMvc
			.perform(get("/api/v1/invitations").header(HttpHeaders.AUTHORIZATION, authorization(carol)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].boardTitle").value("Board"))
			.andExpect(jsonPath("$[0].inviterName").value("Alice"));

		mockMvc
			.perform(get("/api/v1/invitations").header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void sentInvitationsRequireInvitePermission() throws Exception {
		invite(board.getId(), alice, carol.getEmail(), BoardRole.MEMBER);

		mockMvc
			.perform(get("/api/v1/boards/{boardId}/invitations", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isForbidden());

		mockMvc
			.perform(get("/api/v1/boards/{boardId}/invitations", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void userSearchByEmailIsCaseInsensitive() throws Exception {
		mockMvc
			.perform(get("/api/v1/users").param("email", "CAROL@example.com")
				.header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value("Carol"));

		mockMvc
			.perform(get("/api/v1/users").param("email", "nobody@example.com")
				.header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void invitationEndpointsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/invitations")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/invitations/some-token/accept")).andExpect(status().isUnauthorized());
	}

	private InvitationResponse invite(Long boardId, User actor, String email, BoardRole role) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/boards/{boardId}/invitations", boardId)
				.header(HttpHeaders.AUTHORIZATION, authorization(actor))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateInvitationRequest(email, role))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), InvitationResponse.class);
	}

	private String authorization(User user) {
		return "Bearer " + jwtService.generateAccessToken(user);
	}

	private String json(Object value) {
		return objectMapper.writeValueAsString(value);
	}

}
