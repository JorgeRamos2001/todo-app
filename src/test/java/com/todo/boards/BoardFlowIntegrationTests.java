package com.todo.boards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
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
import com.todo.boards.dto.BoardDetailResponse;
import com.todo.boards.dto.CreateBoardRequest;
import com.todo.boards.dto.UpdateBoardRequest;
import com.todo.boards.repository.BoardMemberRepository;
import com.todo.boards.repository.BoardRepository;
import com.todo.users.domain.User;
import com.todo.users.repository.UserRepository;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class BoardFlowIntegrationTests {

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
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	JwtService jwtService;

	private User alice;

	private User bob;

	@BeforeEach
	void setUp() {
		boardMemberRepository.deleteAll();
		boardRepository.deleteAll();
		refreshTokenRepository.deleteAll();
		userRepository.deleteAll();
		alice = userRepository.save(User.local("Alice", "alice@example.com", "hash"));
		bob = userRepository.save(User.local("Bob", "bob@example.com", "hash"));
	}

	@Test
	void createBoardAddsOwnerMembership() throws Exception {
		BoardDetailResponse board = createBoard(alice, "Roadmap", BoardType.COLLABORATIVE);

		assertThat(board.id()).isNotNull();
		assertThat(board.ownerId()).isEqualTo(alice.getId());
		assertThat(board.members()).hasSize(1);
		assertThat(board.members().getFirst().role()).isEqualTo(BoardRole.OWNER);
		assertThat(board.members().getFirst().userId()).isEqualTo(alice.getId());
		assertThat(boardMemberRepository.findByBoardIdAndUserId(board.id(), alice.getId())).isPresent();
	}

	@Test
	void createBoardRejectsInvalidPayload() throws Exception {
		mockMvc.perform(post("/api/v1/boards").header(HttpHeaders.AUTHORIZATION, authorization(alice))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.title").isNotEmpty())
			.andExpect(jsonPath("$.errors.type").isNotEmpty());
	}

	@Test
	void listReturnsOnlyBoardsWhereUserIsMember() throws Exception {
		createBoard(alice, "Alice board", BoardType.COLLABORATIVE);

		mockMvc.perform(get("/api/v1/boards").header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));

		mockMvc.perform(get("/api/v1/boards").header(HttpHeaders.AUTHORIZATION, authorization(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].title").value("Alice board"))
			.andExpect(jsonPath("$[0].role").value("OWNER"));
	}

	@Test
	void nonMembersCannotSeeBoardDetail() throws Exception {
		BoardDetailResponse board = createBoard(alice, "Private", BoardType.COLLABORATIVE);

		mockMvc.perform(get("/api/v1/boards/{id}", board.id()).header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/boards/{id}", board.id()).header(HttpHeaders.AUTHORIZATION, authorization(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Private"));
	}

	@Test
	void onlyOwnerCanUpdateBoard() throws Exception {
		BoardDetailResponse board = createBoard(alice, "Original", BoardType.COLLABORATIVE);
		addMember(board.id(), bob, BoardRole.MEMBER);

		mockMvc.perform(patch("/api/v1/boards/{id}", board.id())
			.header(HttpHeaders.AUTHORIZATION, authorization(bob))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json(new UpdateBoardRequest("Hacked", null))))
			.andExpect(status().isForbidden());

		mockMvc.perform(patch("/api/v1/boards/{id}", board.id())
			.header(HttpHeaders.AUTHORIZATION, authorization(alice))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json(new UpdateBoardRequest("Renamed", "New description"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Renamed"))
			.andExpect(jsonPath("$.description").value("New description"));
	}

	@Test
	void onlyOwnerCanDeleteBoard() throws Exception {
		BoardDetailResponse board = createBoard(alice, "To delete", BoardType.COLLABORATIVE);
		addMember(board.id(), bob, BoardRole.MEMBER);

		mockMvc
			.perform(delete("/api/v1/boards/{id}", board.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isForbidden());

		mockMvc
			.perform(delete("/api/v1/boards/{id}", board.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice)))
			.andExpect(status().isNoContent());

		assertThat(boardRepository.findById(board.id())).isEmpty();
		assertThat(boardMemberRepository.findByBoardId(board.id())).isEmpty();
	}

	@Test
	void membersCanListBoardMembers() throws Exception {
		BoardDetailResponse board = createBoard(alice, "Team", BoardType.COLLABORATIVE);
		addMember(board.id(), bob, BoardRole.ADMIN);

		mockMvc
			.perform(get("/api/v1/boards/{id}/members", board.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void ownerCanRemoveAnyMemberButAdminsOnlyMembers() throws Exception {
		BoardDetailResponse board = createBoard(alice, "Team", BoardType.COLLABORATIVE);
		BoardMember bobMembership = addMember(board.id(), bob, BoardRole.ADMIN);
		User carol = userRepository.save(User.local("Carol", "carol@example.com", "hash"));
		BoardMember carolMembership = addMember(board.id(), carol, BoardRole.ADMIN);

		mockMvc
			.perform(delete("/api/v1/boards/{boardId}/members/{memberId}", board.id(), carolMembership.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isForbidden());

		mockMvc
			.perform(delete("/api/v1/boards/{boardId}/members/{memberId}", board.id(), bobMembership.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice)))
			.andExpect(status().isNoContent());

		assertThat(boardMemberRepository.findById(bobMembership.getId())).isEmpty();
	}

	@Test
	void ownerMembershipCannotBeRemoved() throws Exception {
		BoardDetailResponse board = createBoard(alice, "Team", BoardType.COLLABORATIVE);
		BoardMember aliceMembership = boardMemberRepository
			.findByBoardIdAndUserId(board.id(), alice.getId())
			.orElseThrow();

		mockMvc
			.perform(delete("/api/v1/boards/{boardId}/members/{memberId}", board.id(), aliceMembership.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice)))
			.andExpect(status().isForbidden());
	}

	@Test
	void boardEndpointsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/boards")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/boards").contentType(MediaType.APPLICATION_JSON)
			.content(json(new CreateBoardRequest("Board", null, BoardType.PERSONAL))))
			.andExpect(status().isUnauthorized());
	}

	private BoardDetailResponse createBoard(User owner, String title, BoardType type) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/boards").header(HttpHeaders.AUTHORIZATION, authorization(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateBoardRequest(title, null, type))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), BoardDetailResponse.class);
	}

	private BoardMember addMember(Long boardId, User user, BoardRole role) {
		Board board = boardRepository.findById(boardId).orElseThrow();
		return boardMemberRepository.save(new BoardMember(board, user, role));
	}

	private String authorization(User user) {
		return "Bearer " + jwtService.generateAccessToken(user);
	}

	private String json(Object value) {
		return objectMapper.writeValueAsString(value);
	}

}
